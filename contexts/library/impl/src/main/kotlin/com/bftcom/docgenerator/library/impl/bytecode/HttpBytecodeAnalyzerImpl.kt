package com.bftcom.docgenerator.library.impl.bytecode

import com.bftcom.docgenerator.library.api.bytecode.BytecodeAnalysisResult
import com.bftcom.docgenerator.library.api.bytecode.CallGraph
import com.bftcom.docgenerator.library.api.bytecode.HttpBytecodeAnalyzer
import com.bftcom.docgenerator.library.api.bytecode.HttpCallSite
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

    override fun analyzeJar(jarFile: File): BytecodeAnalysisResult {
        if (!jarFile.exists() || !jarFile.name.endsWith(".jar", ignoreCase = true)) {
            log.debug("Skipping non-jar file: {}", jarFile.name)
            return BytecodeAnalysisResult(
                callGraph = CallGraph(),
                httpCallSites = emptyList(),
                methodSummaries = emptyMap(),
                parentClients = emptySet(),
            )
        }

        val calls = mutableMapOf<MethodId, MutableSet<MethodId>>()
        val httpCallSites = mutableListOf<HttpCallSite>()

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
                            analyzeClass(input, calls, httpCallSites)
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
        val parentClients = findParentClients(httpCallSites, callGraph)
        val methodSummaries = buildMethodSummaries(httpCallSites, callGraph, parentClients)

        log.info(
            "Analysis completed: calls={}, httpCalls={}, parentClients={}",
            calls.size,
            httpCallSites.size,
            parentClients.size,
        )

        return BytecodeAnalysisResult(
            callGraph = callGraph,
            httpCallSites = httpCallSites,
            methodSummaries = methodSummaries,
            parentClients = parentClients,
        )
    }

    private fun analyzeClass(
        input: InputStream,
        calls: MutableMap<MethodId, MutableSet<MethodId>>,
        httpCallSites: MutableList<HttpCallSite>,
    ) {
        val reader = ClassReader(input.readBytes())
        val visitor = HttpAnalysisClassVisitor(calls, httpCallSites)
        // УБИРАЕМ SKIP_CODE - теперь анализируем код методов!
        reader.accept(visitor, ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)
    }

    private inner class HttpAnalysisClassVisitor(
        private val calls: MutableMap<MethodId, MutableSet<MethodId>>,
        private val httpCallSites: MutableList<HttpCallSite>,
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

            return HttpAnalysisMethodVisitor(methodId, calls, httpCallSites, webClientOwners, restTemplateOwner)
        }
    }

    private inner class HttpAnalysisMethodVisitor(
        private val methodId: MethodId,
        private val calls: MutableMap<MethodId, MutableSet<MethodId>>,
        private val httpCallSites: MutableList<HttpCallSite>,
        private val webClientOwners: Set<String>,
        private val restTemplateOwner: String,
    ) : MethodVisitor(Opcodes.ASM9) {
        private val callees = mutableSetOf<MethodId>()
        private var currentUrl: String? = null
        private var currentHttpMethod: String? = null
        private var currentClientType: String? = null
        private var hasRetry = false
        private var hasTimeout = false
        private var hasCircuitBreaker = false

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
            }
        }

        override fun visitLdcInsn(cst: Any?) {
            // Простой анализ: если встречаем строковую константу после вызова uri(),
            // это может быть URL
            if (cst is String && currentClientType != null && currentUrl == null) {
                // Простая эвристика: если строка похожа на URL
                if (cst.startsWith("http://") || cst.startsWith("https://") || cst.startsWith("/")) {
                    currentUrl = cst
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
        }
    }

    /**
     * Фаза 2: Находим родительские клиенты, поднимаясь вверх по call graph.
     */
    private fun findParentClients(
        httpCallSites: List<HttpCallSite>,
        callGraph: CallGraph,
    ): Set<MethodId> {
        val parentClients = mutableSetOf<MethodId>()
        val visited = mutableSetOf<MethodId>()

        // Стартовые точки - все методы с HTTP-вызовами
        val startMethods = httpCallSites.map { it.methodId }.toSet()

        for (startMethod in startMethods) {
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
        callGraph: CallGraph,
        parentClients: Set<MethodId>,
    ): Map<MethodId, MethodSummary> {
        val summaries = mutableMapOf<MethodId, MethodSummary>()

        // Сначала собираем прямые HTTP-вызовы
        val directCallsByMethod = httpCallSites.groupBy { it.methodId }

        // Собираем сводки снизу вверх (от листьев к корням)
        val allMethods = (httpCallSites.map { it.methodId } + parentClients).toSet()
        val processed = mutableSetOf<MethodId>()

        fun processMethod(methodId: MethodId): MethodSummary {
            if (methodId in processed) {
                return summaries[methodId] ?: MethodSummary(methodId)
            }
            processed.add(methodId)

            val directCalls = directCallsByMethod[methodId] ?: emptyList()
            val callees = callGraph.getCallees(methodId)

            // Собираем сводки от вызываемых методов
            val calleeSummaries = callees.map { processMethod(it) }

            // Объединяем информацию
            val urls = (directCalls.mapNotNull { it.url } + calleeSummaries.flatMap { it.urls }).toSet()
            val httpMethods = (directCalls.mapNotNull { it.httpMethod } + calleeSummaries.flatMap { it.httpMethods }).toSet()
            val hasRetry = directCalls.any { it.hasRetry } || calleeSummaries.any { it.hasRetry }
            val hasTimeout = directCalls.any { it.hasTimeout } || calleeSummaries.any { it.hasTimeout }
            val hasCircuitBreaker = directCalls.any { it.hasCircuitBreaker } || calleeSummaries.any { it.hasCircuitBreaker }

            val summary = MethodSummary(
                methodId = methodId,
                urls = urls,
                httpMethods = httpMethods,
                hasRetry = hasRetry,
                hasTimeout = hasTimeout,
                hasCircuitBreaker = hasCircuitBreaker,
                directHttpCalls = directCalls,
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

