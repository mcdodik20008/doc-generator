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
import org.objectweb.asm.Type
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
    private val webClientOwners = setOf(
        "org/springframework/web/reactive/function/client/WebClient",
        "org/springframework/web/reactive/function/client/WebClient\$UriSpec",
        "org/springframework/web/reactive/function/client/WebClient\$RequestBodySpec",
        "org/springframework/web/reactive/function/client/WebClient\$RequestHeadersSpec",
        "org/springframework/web/reactive/function/client/WebClient\$ResponseSpec",
    )

    private val restTemplateOwner = "org/springframework/web/client/RestTemplate"

    // Методы для определения HTTP-метода
    private val httpMethodNames = setOf("get", "post", "put", "delete", "patch", "head", "options")

    // Паттерны для поиска Kafka-клиентов
    private val kafkaProducerOwner = "org/apache/kafka/clients/producer/KafkaProducer"
    private val kafkaConsumerOwner = "org/apache/kafka/clients/consumer/KafkaConsumer"

    // Паттерны для поиска Camel
    private val camelRouteBuilderOwner = "org/apache/camel/builder/RouteBuilder"
    private val camelEndpointOwners = setOf(
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
                            analyzeClass(input, calls, httpCallSites, kafkaCallSites, camelCallSites)
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
        val allCallSites = httpCallSites.map { it.methodId } + 
            kafkaCallSites.map { it.methodId } + 
            camelCallSites.map { it.methodId }
        val parentClients = findParentClients(allCallSites.toSet(), callGraph)
        val methodSummaries = buildMethodSummaries(
            httpCallSites, 
            kafkaCallSites, 
            camelCallSites, 
            callGraph, 
            parentClients
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
        httpCallSites: MutableList<HttpCallSite>,
        kafkaCallSites: MutableList<KafkaCallSite>,
        camelCallSites: MutableList<CamelCallSite>,
    ) {
        val reader = ClassReader(input.readBytes())
        val visitor = HttpAnalysisClassVisitor(calls, httpCallSites, kafkaCallSites, camelCallSites)
        // УБИРАЕМ SKIP_CODE - теперь анализируем код методов!
        reader.accept(visitor, ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)
    }

    private inner class HttpAnalysisClassVisitor(
        private val calls: MutableMap<MethodId, MutableSet<MethodId>>,
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

            return HttpAnalysisMethodVisitor(
                methodId, 
                calls, 
                httpCallSites, 
                kafkaCallSites,
                camelCallSites,
                webClientOwners, 
                restTemplateOwner,
                kafkaProducerOwner,
                kafkaConsumerOwner,
                camelRouteBuilderOwner,
                camelEndpointOwners,
            )
        }
    }

    private inner class HttpAnalysisMethodVisitor(
        private val methodId: MethodId,
        private val calls: MutableMap<MethodId, MutableSet<MethodId>>,
        private val httpCallSites: MutableList<HttpCallSite>,
        private val kafkaCallSites: MutableList<KafkaCallSite>,
        private val camelCallSites: MutableList<CamelCallSite>,
        private val webClientOwners: Set<String>,
        private val restTemplateOwner: String,
        private val kafkaProducerOwner: String,
        private val kafkaConsumerOwner: String,
        private val camelRouteBuilderOwner: String,
        private val camelEndpointOwners: Set<String>,
    ) : MethodVisitor(Opcodes.ASM9) {
        private val callees = mutableSetOf<MethodId>()
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
                            // URL будет на стеке, но мы его не можем извлечь без интерпретации
                            // Пока оставляем null
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
            if (cst !is String) return
            
            // HTTP: URL
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

        override fun visitEnd() {
            // Сохраняем граф вызовов
            if (callees.isNotEmpty()) {
                calls[methodId] = callees
            }

            // Если нашли HTTP-вызов, сохраняем его
            if (currentClientType != null) {
                httpCallSites.add(
                    HttpCallSite(
                        methodId = methodId,
                        url = currentUrl,
                        httpMethod = currentHttpMethod,
                        clientType = currentClientType!!,
                        hasRetry = hasRetry,
                        hasTimeout = hasTimeout,
                        hasCircuitBreaker = hasCircuitBreaker,
                    ),
                )
            }
            
            // Если нашли Kafka-вызов, сохраняем его
            if (currentKafkaClientType != null) {
                kafkaCallSites.add(
                    KafkaCallSite(
                        methodId = methodId,
                        topic = currentKafkaTopic,
                        operation = currentKafkaOperation ?: "UNKNOWN",
                        clientType = currentKafkaClientType!!,
                    ),
                )
            }
            
            // Если нашли Camel-вызов, сохраняем его
            if (currentCamelDirection != null && currentCamelUri != null) {
                camelCallSites.add(
                    CamelCallSite(
                        methodId = methodId,
                        uri = currentCamelUri,
                        endpointType = currentCamelEndpointType,
                        direction = currentCamelDirection!!,
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
    ): Set<MethodId> {
        val parentClients = mutableSetOf<MethodId>()
        val visited = mutableSetOf<MethodId>()

        // Стартовые точки - все методы с интеграционными вызовами
        for (startMethod in callSiteMethodIds) {
            findParentClientRecursive(startMethod, callGraph, visited, parentClients)
        }

        return parentClients.toSet()
    }

    private fun findParentClientRecursive(
        methodId: MethodId,
        callGraph: CallGraph,
        visited: MutableSet<MethodId>,
        parentClients: MutableSet<MethodId>,
    ) {
        if (methodId in visited) return
        visited.add(methodId)

        // Проверяем, является ли текущий метод родительским клиентом
        if (isParentClientMethod(methodId)) {
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
            findParentClientRecursive(caller, callGraph, visited, parentClients)
        }
    }

    /**
     * Проверяет, является ли метод родительским клиентом.
     * Критерии:
     * - public метод
     * - не synthetic, не bridge
     * - находится в пакете с "client" или класс заканчивается на "Client"/"Gateway"
     */
    private fun isParentClientMethod(methodId: MethodId): Boolean {
        val ownerFqn = methodId.ownerFqn
        val packageName = ownerFqn.substringBeforeLast('.')
        val className = ownerFqn.substringAfterLast('.')

        // Проверяем пакет и имя класса
        val isClientPackage = "client" in packageName.lowercase() ||
            className.endsWith("Client") ||
            className.endsWith("Gateway") ||
            className.endsWith("Service")

        // TODO: можно добавить проверку модификаторов, если они доступны
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
        val allMethods = (
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
            val kafkaTopics = (
                directKafkaCalls.mapNotNull { it.topic } + 
                calleeSummaries.flatMap { it.kafkaTopics }
            ).toSet()
            
            // Camel
            val camelUris = (
                directCamelCalls.mapNotNull { it.uri } + 
                calleeSummaries.flatMap { it.camelUris }
            ).toSet()

            val summary = MethodSummary(
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

