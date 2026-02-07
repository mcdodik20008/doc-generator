package com.bftcom.docgenerator.library.impl.bytecode

import com.bftcom.docgenerator.library.api.bytecode.BytecodeAnalysisResult
import com.bftcom.docgenerator.library.api.bytecode.CallGraph
import com.bftcom.docgenerator.library.api.bytecode.CamelCallSite
import com.bftcom.docgenerator.library.api.bytecode.HttpBytecodeAnalyzer
import com.bftcom.docgenerator.library.api.bytecode.HttpCallSite
import com.bftcom.docgenerator.library.api.bytecode.KafkaCallSite
import com.bftcom.docgenerator.library.api.bytecode.MethodId
import com.bftcom.docgenerator.library.api.bytecode.MethodSummary
import org.objectweb.asm.ClassReader
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.File
import java.io.InputStream
import java.util.jar.JarFile
import java.util.zip.ZipEntry

/**
 * Реализация анализатора байткода для поиска HTTP-вызовов.
 * Базовый MVP-вариант: простой анализ без сложной интерпретации стека.
 */
@Component
class HttpBytecodeAnalyzerImpl : HttpBytecodeAnalyzer {
    private val log = LoggerFactory.getLogger(javaClass)

    // Паттерны для поиска HTTP-клиентов
    private val webClientOwners =
        setOf(
            "org/springframework/web/reactive/function/client/WebClient",
            "org/springframework/web/reactive/function/client/WebClient\$UriSpec",
            "org/springframework/web/reactive/function/client/WebClient\$RequestBodySpec",
            "org/springframework/web/reactive/function/client/WebClient\$RequestHeadersSpec",
            "org/springframework/web/reactive/function/client/WebClient\$ResponseSpec",
        )

    private val restTemplateOwner = "org/springframework/web/client/RestTemplate"

    // OkHttp
    private val okHttpClientOwner = "okhttp3/OkHttpClient"
    private val okHttpRequestOwner = "okhttp3/Request"
    private val okHttpRequestBuilderOwner = "okhttp3/Request\$Builder"
    private val okHttpCallOwner = "okhttp3/Call"

    // Apache HttpClient
    private val apacheHttpClientOwner = "org/apache/http/client/HttpClient"
    private val apacheHttpGetOwner = "org/apache/http/client/methods/HttpGet"
    private val apacheHttpPostOwner = "org/apache/http/client/methods/HttpPost"
    private val apacheHttpPutOwner = "org/apache/http/client/methods/HttpPut"
    private val apacheHttpDeleteOwner = "org/apache/http/client/methods/HttpDelete"
    private val apacheHttpPatchOwner = "org/apache/http/client/methods/HttpPatch"
    private val apacheHttpUriRequestOwner = "org/apache/http/client/methods/HttpUriRequest"
    private val apacheHttpRequestBaseOwner = "org/apache/http/client/methods/HttpRequestBase"

    // Методы для определения HTTP-метода
    private val httpMethodNames = setOf("get", "post", "put", "delete", "patch", "head", "options")

    // Паттерны для поиска Kafka-клиентов
    private val kafkaProducerOwner = "org/apache/kafka/clients/producer/KafkaProducer"
    private val kafkaConsumerOwner = "org/apache/kafka/clients/consumer/KafkaConsumer"

    // Паттерны для поиска Camel
    private val camelRouteBuilderOwner = "org/apache/camel/builder/RouteBuilder"
    private val camelEndpointOwners =
        setOf(
            "org/apache/camel/Route",
            "org/apache/camel/builder/RouteBuilder",
        )

    override fun analyzeJar(jarFile: File): BytecodeAnalysisResult {
        if (!jarFile.exists() || !jarFile.name.endsWith(".jar", ignoreCase = true)) {
            log.debug("Skipping non-jar file: {}", jarFile.name)
            return BytecodeAnalysisResult(
                callGraph = CallGraph(),
                httpCallSites = emptyList(),
                kafkaCallSites = emptyList(),
                camelCallSites = emptyList(),
                methodSummaries = emptyMap(),
                parentClients = emptySet(),
            )
        }

        val calls = mutableMapOf<MethodId, MutableSet<MethodId>>()
        val methodAccessFlags = mutableMapOf<MethodId, Int>() // Храним access flags для проверки модификаторов
        val httpCallSites = mutableListOf<HttpCallSite>()
        val kafkaCallSites = mutableListOf<KafkaCallSite>()
        val camelCallSites = mutableListOf<CamelCallSite>()

        try {
            JarFile(jarFile).use { jar ->
                val classEntries = mutableListOf<ZipEntry>()
                val entriesEnum = jar.entries()
                while (entriesEnum.hasMoreElements()) {
                    val entry = entriesEnum.nextElement()
                    if (!entry.isDirectory && entry.name.endsWith(".class")) {
                        classEntries += entry
                    }
                }

                log.info("Analyzing jar {} ({} class files)", jarFile.name, classEntries.size)

                for (entry in classEntries) {
                    try {
                        jar.getInputStream(entry).use { input ->
                            analyzeClass(input, calls, methodAccessFlags, httpCallSites, kafkaCallSites, camelCallSites)
                        }
                    } catch (e: Exception) {
                        log.debug("Failed to analyze class {}: {}", entry.name, e.message)
                    }
                }
            }
        } catch (e: Exception) {
            log.warn("Failed to analyze jar {}: {}", jarFile.name, e.message)
        }

        val callGraph = CallGraph.build(calls)
        val allCallSites =
            httpCallSites.map { it.methodId } +
                kafkaCallSites.map { it.methodId } +
                camelCallSites.map { it.methodId }
        val parentClients = findParentClients(allCallSites.toSet(), callGraph, methodAccessFlags)
        val methodSummaries =
            buildMethodSummaries(
                httpCallSites,
                kafkaCallSites,
                camelCallSites,
                callGraph,
                parentClients,
            )

        log.info(
            "Analysis completed: calls={}, httpCalls={}, kafkaCalls={}, camelCalls={}, parentClients={}",
            calls.size,
            httpCallSites.size,
            kafkaCallSites.size,
            camelCallSites.size,
            parentClients.size,
        )

        return BytecodeAnalysisResult(
            callGraph = callGraph,
            httpCallSites = httpCallSites,
            kafkaCallSites = kafkaCallSites,
            camelCallSites = camelCallSites,
            methodSummaries = methodSummaries,
            parentClients = parentClients,
        )
    }

    private fun analyzeClass(
        input: InputStream,
        calls: MutableMap<MethodId, MutableSet<MethodId>>,
        methodAccessFlags: MutableMap<MethodId, Int>,
        httpCallSites: MutableList<HttpCallSite>,
        kafkaCallSites: MutableList<KafkaCallSite>,
        camelCallSites: MutableList<CamelCallSite>,
    ) {
        val reader = ClassReader(input.readBytes())
        val visitor = HttpAnalysisClassVisitor(calls, methodAccessFlags, httpCallSites, kafkaCallSites, camelCallSites)
        // УБИРАЕМ SKIP_CODE - теперь анализируем код методов!
        reader.accept(visitor, ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)
    }

    private inner class HttpAnalysisClassVisitor(
        private val calls: MutableMap<MethodId, MutableSet<MethodId>>,
        private val methodAccessFlags: MutableMap<MethodId, Int>,
        private val httpCallSites: MutableList<HttpCallSite>,
        private val kafkaCallSites: MutableList<KafkaCallSite>,
        private val camelCallSites: MutableList<CamelCallSite>,
    ) : org.objectweb.asm.ClassVisitor(Opcodes.ASM9) {
        private var currentOwner: String? = null

        override fun visit(
            version: Int,
            access: Int,
            name: String,
            signature: String?,
            superName: String?,
            interfaces: Array<out String>?,
        ) {
            currentOwner = name
        }

        override fun visitMethod(
            access: Int,
            name: String,
            descriptor: String,
            signature: String?,
            exceptions: Array<out String>?,
        ): MethodVisitor? {
            // Пропускаем статические инициализаторы
            if (name == "<clinit>") return null

            val owner = currentOwner ?: return null
            val methodId = MethodId(owner, name, descriptor)

            // Сохраняем access flags
            methodAccessFlags[methodId] = access

            return HttpAnalysisMethodVisitor(
                methodId,
                access,
                calls,
                httpCallSites,
                kafkaCallSites,
                camelCallSites,
                webClientOwners,
                restTemplateOwner,
                okHttpClientOwner,
                okHttpRequestOwner,
                okHttpRequestBuilderOwner,
                okHttpCallOwner,
                apacheHttpGetOwner,
                apacheHttpPostOwner,
                apacheHttpPutOwner,
                apacheHttpDeleteOwner,
                apacheHttpPatchOwner,
                kafkaProducerOwner,
                kafkaConsumerOwner,
                camelRouteBuilderOwner,
                camelEndpointOwners,
            )
        }
    }

    private inner class HttpAnalysisMethodVisitor(
        private val methodId: MethodId,
        private val methodAccess: Int,
        private val calls: MutableMap<MethodId, MutableSet<MethodId>>,
        private val httpCallSites: MutableList<HttpCallSite>,
        private val kafkaCallSites: MutableList<KafkaCallSite>,
        private val camelCallSites: MutableList<CamelCallSite>,
        private val webClientOwners: Set<String>,
        private val restTemplateOwner: String,
        private val okHttpClientOwner: String,
        private val okHttpRequestOwner: String,
        private val okHttpRequestBuilderOwner: String,
        private val okHttpCallOwner: String,
        private val apacheHttpGetOwner: String,
        private val apacheHttpPostOwner: String,
        private val apacheHttpPutOwner: String,
        private val apacheHttpDeleteOwner: String,
        private val apacheHttpPatchOwner: String,
        private val kafkaProducerOwner: String,
        private val kafkaConsumerOwner: String,
        private val camelRouteBuilderOwner: String,
        private val camelEndpointOwners: Set<String>,
    ) : MethodVisitor(Opcodes.ASM9) {
        private val callees = mutableSetOf<MethodId>()
        private val stackInterpreter = StackInterpreter()
        private var currentUrl: String? = null
        private var currentHttpMethod: String? = null
        private var currentClientType: String? = null
        private var hasRetry = false
        private var hasTimeout = false
        private var hasCircuitBreaker = false

        // Kafka
        private var currentKafkaTopic: String? = null
        private var currentKafkaOperation: String? = null
        private var currentKafkaClientType: String? = null

        // Camel
        private var currentCamelUri: String? = null
        private var currentCamelDirection: String? = null
        private var currentCamelEndpointType: String? = null

        override fun visitMethodInsn(
            opcode: Int,
            owner: String,
            name: String,
            descriptor: String,
            isInterface: Boolean,
        ) {
            val calleeId = MethodId(owner, name, descriptor)
            callees.add(calleeId)

            // Обрабатываем StringBuilder операции
            if (owner == "java/lang/StringBuilder") {
                when (name) {
                    "append" -> {
                        stackInterpreter.visitStringBuilderAppend(descriptor)
                    }
                    "toString" -> {
                        stackInterpreter.visitStringBuilderToString()
                        // После toString() можем извлечь URL если это строка
                        if (currentClientType != null && currentUrl == null) {
                            val urlFromStack = stackInterpreter.peekString()
                            if (urlFromStack != null &&
                                (
                                    urlFromStack.startsWith("http://") ||
                                        urlFromStack.startsWith("https://") ||
                                        urlFromStack.startsWith("/")
                                )
                            ) {
                                currentUrl = urlFromStack
                            }
                        }
                    }
                }
            }

            // Обрабатываем конкатенацию строк через String.concat
            if (owner == "java/lang/String" && name == "concat") {
                stackInterpreter.visitStringConcat()
                // После конкатенации проверяем URL
                if (currentClientType != null && currentUrl == null) {
                    val urlFromStack = stackInterpreter.peekString()
                    if (urlFromStack != null &&
                        (
                            urlFromStack.startsWith("http://") ||
                                urlFromStack.startsWith("https://") ||
                                urlFromStack.startsWith("/")
                        )
                    ) {
                        currentUrl = urlFromStack
                    }
                }
            }

            // Проверяем, является ли это вызовом HTTP-клиента
            when {
                owner in webClientOwners -> {
                    currentClientType = "WebClient"
                    // Определяем HTTP-метод по имени метода
                    val method = name.lowercase()
                    if (method in httpMethodNames) {
                        currentHttpMethod = method.uppercase()
                    }
                    // Проверяем методы цепочки
                    when (name) {
                        "retry" -> hasRetry = true
                        "timeout" -> hasTimeout = true
                        "uri" -> {
                            // URL из стека
                            val urlFromStack = stackInterpreter.popStringArg()
                            if (urlFromStack != null && currentUrl == null) {
                                currentUrl = urlFromStack
                            }
                        }
                    }
                }
                owner == restTemplateOwner -> {
                    currentClientType = "RestTemplate"
                    // RestTemplate методы: getForObject, postForObject и т.д.
                    val method = name.lowercase()
                    when {
                        method.startsWith("get") -> currentHttpMethod = "GET"
                        method.startsWith("post") -> currentHttpMethod = "POST"
                        method.startsWith("put") -> currentHttpMethod = "PUT"
                        method.startsWith("delete") -> currentHttpMethod = "DELETE"
                        method.startsWith("patch") -> currentHttpMethod = "PATCH"
                    }
                    // URL может быть в параметрах метода
                    val urlFromStack = stackInterpreter.popStringArg()
                    if (urlFromStack != null && currentUrl == null) {
                        currentUrl = urlFromStack
                    }
                }
                // OkHttp Request.Builder
                owner == okHttpRequestBuilderOwner -> {
                    currentClientType = "OkHttp"
                    when (name) {
                        "url" -> {
                            // URL из стека
                            val urlFromStack = stackInterpreter.popStringArg()
                            if (urlFromStack != null) {
                                currentUrl = urlFromStack
                            }
                        }
                        "get", "post", "put", "delete", "patch", "head" -> {
                            // HTTP-метод определяется по имени метода
                            currentHttpMethod = name.uppercase()
                        }
                    }
                }
                // OkHttp Request
                owner == okHttpRequestOwner && name == "newBuilder" -> {
                    currentClientType = "OkHttp"
                }
                // OkHttp Call.execute() или enqueue()
                owner == okHttpCallOwner -> {
                    if (currentClientType == null) {
                        currentClientType = "OkHttp"
                    }
                }
                // Apache HttpClient - HttpGet
                owner == apacheHttpGetOwner -> {
                    currentClientType = "ApacheHttpClient"
                    currentHttpMethod = "GET"
                    // URL в конструкторе
                    val urlFromStack = stackInterpreter.popStringArg()
                    if (urlFromStack != null) {
                        currentUrl = urlFromStack
                    }
                }
                // Apache HttpClient - HttpPost
                owner == apacheHttpPostOwner -> {
                    currentClientType = "ApacheHttpClient"
                    currentHttpMethod = "POST"
                    val urlFromStack = stackInterpreter.popStringArg()
                    if (urlFromStack != null) {
                        currentUrl = urlFromStack
                    }
                }
                // Apache HttpClient - HttpPut
                owner == apacheHttpPutOwner -> {
                    currentClientType = "ApacheHttpClient"
                    currentHttpMethod = "PUT"
                    val urlFromStack = stackInterpreter.popStringArg()
                    if (urlFromStack != null) {
                        currentUrl = urlFromStack
                    }
                }
                // Apache HttpClient - HttpDelete
                owner == apacheHttpDeleteOwner -> {
                    currentClientType = "ApacheHttpClient"
                    currentHttpMethod = "DELETE"
                    val urlFromStack = stackInterpreter.popStringArg()
                    if (urlFromStack != null) {
                        currentUrl = urlFromStack
                    }
                }
                // Apache HttpClient - HttpPatch
                owner == apacheHttpPatchOwner -> {
                    currentClientType = "ApacheHttpClient"
                    currentHttpMethod = "PATCH"
                    val urlFromStack = stackInterpreter.popStringArg()
                    if (urlFromStack != null) {
                        currentUrl = urlFromStack
                    }
                }
                // Kafka Producer
                owner == kafkaProducerOwner -> {
                    currentKafkaClientType = "KafkaProducer"
                    currentKafkaOperation = "PRODUCE"
                    if (name == "send") {
                        // Топик будет в параметрах, но без интерпретации стека сложно извлечь
                    }
                }
                // Kafka Consumer
                owner == kafkaConsumerOwner -> {
                    currentKafkaClientType = "KafkaConsumer"
                    currentKafkaOperation = "CONSUME"
                    when (name) {
                        "subscribe" -> {
                            // Топик будет в параметрах
                        }
                        "poll" -> {
                            // Операция чтения
                        }
                    }
                }
                // Camel RouteBuilder
                owner == camelRouteBuilderOwner -> {
                    when (name) {
                        "from" -> {
                            currentCamelDirection = "FROM"
                        }
                        "to" -> {
                            currentCamelDirection = "TO"
                        }
                    }
                }
                // Camel endpoints (from/to могут быть вызваны на Route)
                owner in camelEndpointOwners -> {
                    when (name) {
                        "from" -> {
                            currentCamelDirection = "FROM"
                        }
                        "to" -> {
                            currentCamelDirection = "TO"
                        }
                    }
                }
            }
        }

        override fun visitLdcInsn(cst: Any?) {
            // Всегда добавляем в интерпретатор стека
            stackInterpreter.visitLdc(cst)

            if (cst !is String) return

            // HTTP: URL (используем интерпретатор стека)
            if (currentClientType != null && currentUrl == null) {
                // Простая эвристика: если строка похожа на URL
                if (cst.startsWith("http://") || cst.startsWith("https://") || cst.startsWith("/")) {
                    currentUrl = cst
                }
            }

            // Kafka: топик (простая эвристика - любая строка после Kafka-вызова)
            if (currentKafkaClientType != null && currentKafkaTopic == null) {
                // Топики обычно не содержат пробелов и специальных символов
                if (cst.isNotBlank() && !cst.contains(" ") && cst.length < 200) {
                    currentKafkaTopic = cst
                }
            }

            // Camel: URI endpoint
            if (currentCamelDirection != null && currentCamelUri == null) {
                // Camel URI обычно имеют формат: "kafka:topic", "http://host/path", "jms:queue"
                if (cst.contains(":") || cst.startsWith("http://") || cst.startsWith("https://")) {
                    currentCamelUri = cst
                    // Определяем тип endpoint по префиксу
                    val colonIndex = cst.indexOf(':')
                    if (colonIndex > 0) {
                        currentCamelEndpointType = cst.substring(0, colonIndex)
                    }
                }
            }
        }

        override fun visitTypeInsn(
            opcode: Int,
            type: String,
        ) {
            // NEW StringBuilder
            if (opcode == Opcodes.NEW && type == "java/lang/StringBuilder") {
                stackInterpreter.visitNewStringBuilder()
            }
        }

        override fun visitEnd() {
            // Сохраняем граф вызовов
            if (callees.isNotEmpty()) {
                calls[methodId] = callees
            }

            // Если нашли HTTP-вызов, сохраняем его
            currentClientType?.let { clientType ->
                httpCallSites.add(
                    HttpCallSite(
                        methodId = methodId,
                        url = currentUrl,
                        httpMethod = currentHttpMethod,
                        clientType = clientType,
                        hasRetry = hasRetry,
                        hasTimeout = hasTimeout,
                        hasCircuitBreaker = hasCircuitBreaker,
                    ),
                )
            }

            // Если нашли Kafka-вызов, сохраняем его
            currentKafkaClientType?.let { kafkaClientType ->
                kafkaCallSites.add(
                    KafkaCallSite(
                        methodId = methodId,
                        topic = currentKafkaTopic,
                        operation = currentKafkaOperation ?: "UNKNOWN",
                        clientType = kafkaClientType,
                    ),
                )
            }

            // Если нашли Camel-вызов, сохраняем его
            val camelDir = currentCamelDirection
            if (camelDir != null && currentCamelUri != null) {
                camelCallSites.add(
                    CamelCallSite(
                        methodId = methodId,
                        uri = currentCamelUri,
                        endpointType = currentCamelEndpointType,
                        direction = camelDir,
                    ),
                )
            }
        }
    }

    /**
     * Фаза 2: Находим родительские клиенты, поднимаясь вверх по call graph.
     */
    private fun findParentClients(
        callSiteMethodIds: Set<MethodId>,
        callGraph: CallGraph,
        methodAccessFlags: Map<MethodId, Int>,
    ): Set<MethodId> {
        val parentClients = mutableSetOf<MethodId>()
        val visited = mutableSetOf<MethodId>()

        // Стартовые точки - все методы с интеграционными вызовами
        for (startMethod in callSiteMethodIds) {
            findParentClientRecursive(startMethod, callGraph, visited, parentClients, methodAccessFlags)
        }

        return parentClients.toSet()
    }

    private fun findParentClientRecursive(
        methodId: MethodId,
        callGraph: CallGraph,
        visited: MutableSet<MethodId>,
        parentClients: MutableSet<MethodId>,
        methodAccessFlags: Map<MethodId, Int>,
    ) {
        if (methodId in visited) return
        visited.add(methodId)

        // Проверяем, является ли текущий метод родительским клиентом
        val accessFlags = methodAccessFlags[methodId] ?: 0
        if (isParentClientMethod(methodId, accessFlags)) {
            parentClients.add(methodId)
            // Останавливаемся здесь - это верхний уровень
            return
        }

        val callers = callGraph.getCallers(methodId)
        if (callers.isEmpty()) {
            // Дошли до верха, но метод не является родительским клиентом
            // Это может быть метод, который напрямую вызывает WebClient/RestTemplate
            // но не находится в клиентском пакете
            return
        }

        // Поднимаемся дальше
        for (caller in callers) {
            findParentClientRecursive(caller, callGraph, visited, parentClients, methodAccessFlags)
        }
    }

    /**
     * Проверяет, является ли метод родительским клиентом.
     * Критерии:
     * - public метод
     * - не synthetic, не bridge
     * - находится в пакете с "client" или класс заканчивается на "Client"/"Gateway"/"Service"
     */
    private fun isParentClientMethod(
        methodId: MethodId,
        accessFlags: Int,
    ): Boolean {
        val ownerFqn = methodId.ownerFqn
        val packageName = ownerFqn.substringBeforeLast('.')
        val className = ownerFqn.substringAfterLast('.')

        // Проверяем модификаторы
        val isPublic = (accessFlags and Opcodes.ACC_PUBLIC) != 0
        val isSynthetic = (accessFlags and Opcodes.ACC_SYNTHETIC) != 0
        val isBridge = (accessFlags and Opcodes.ACC_BRIDGE) != 0

        // Должен быть public и не synthetic/bridge
        if (!isPublic || isSynthetic || isBridge) {
            return false
        }

        // Проверяем пакет и имя класса
        val isClientPackage =
            "client" in packageName.lowercase() ||
                className.endsWith("Client") ||
                className.endsWith("Gateway") ||
                className.endsWith("Service")

        return isClientPackage
    }

    /**
     * Фаза 3: Собираем сводки по методам.
     */
    private fun buildMethodSummaries(
        httpCallSites: List<HttpCallSite>,
        kafkaCallSites: List<KafkaCallSite>,
        camelCallSites: List<CamelCallSite>,
        callGraph: CallGraph,
        parentClients: Set<MethodId>,
    ): Map<MethodId, MethodSummary> {
        val summaries = mutableMapOf<MethodId, MethodSummary>()

        // Собираем прямые вызовы по методам
        val directHttpCallsByMethod = httpCallSites.groupBy { it.methodId }
        val directKafkaCallsByMethod = kafkaCallSites.groupBy { it.methodId }
        val directCamelCallsByMethod = camelCallSites.groupBy { it.methodId }

        // Собираем сводки снизу вверх (от листьев к корням)
        val allMethods =
            (
                httpCallSites.map { it.methodId } +
                    kafkaCallSites.map { it.methodId } +
                    camelCallSites.map { it.methodId } +
                    parentClients
            ).toSet()
        val processed = mutableSetOf<MethodId>()

        fun processMethod(methodId: MethodId): MethodSummary {
            if (methodId in processed) {
                return summaries[methodId] ?: MethodSummary(methodId)
            }
            processed.add(methodId)

            val directHttpCalls = directHttpCallsByMethod[methodId] ?: emptyList()
            val directKafkaCalls = directKafkaCallsByMethod[methodId] ?: emptyList()
            val directCamelCalls = directCamelCallsByMethod[methodId] ?: emptyList()
            val callees = callGraph.getCallees(methodId)

            // Собираем сводки от вызываемых методов
            val calleeSummaries = callees.map { processMethod(it) }

            // Объединяем информацию
            val urls = (directHttpCalls.mapNotNull { it.url } + calleeSummaries.flatMap { it.urls }).toSet()
            val httpMethods = (directHttpCalls.mapNotNull { it.httpMethod } + calleeSummaries.flatMap { it.httpMethods }).toSet()
            val hasRetry = directHttpCalls.any { it.hasRetry } || calleeSummaries.any { it.hasRetry }
            val hasTimeout = directHttpCalls.any { it.hasTimeout } || calleeSummaries.any { it.hasTimeout }
            val hasCircuitBreaker = directHttpCalls.any { it.hasCircuitBreaker } || calleeSummaries.any { it.hasCircuitBreaker }

            // Kafka
            val kafkaTopics =
                (
                    directKafkaCalls.mapNotNull { it.topic } +
                        calleeSummaries.flatMap { it.kafkaTopics }
                ).toSet()

            // Camel
            val camelUris =
                (
                    directCamelCalls.mapNotNull { it.uri } +
                        calleeSummaries.flatMap { it.camelUris }
                ).toSet()

            val summary =
                MethodSummary(
                    methodId = methodId,
                    urls = urls,
                    httpMethods = httpMethods,
                    hasRetry = hasRetry,
                    hasTimeout = hasTimeout,
                    hasCircuitBreaker = hasCircuitBreaker,
                    directHttpCalls = directHttpCalls,
                    kafkaTopics = kafkaTopics,
                    directKafkaCalls = directKafkaCalls,
                    camelUris = camelUris,
                    directCamelCalls = directCamelCalls,
                    isParentClient = methodId in parentClients,
                )

            summaries[methodId] = summary
            return summary
        }

        // Обрабатываем все методы
        for (methodId in allMethods) {
            processMethod(methodId)
        }

        return summaries
    }
}
