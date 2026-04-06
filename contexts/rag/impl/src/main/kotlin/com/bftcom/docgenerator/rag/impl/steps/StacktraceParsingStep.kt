package com.bftcom.docgenerator.rag.impl.steps

import com.bftcom.docgenerator.rag.api.ProcessingStep
import com.bftcom.docgenerator.rag.api.ProcessingStepStatus
import com.bftcom.docgenerator.rag.api.ProcessingStepType
import com.bftcom.docgenerator.rag.api.QueryMetadataKeys
import com.bftcom.docgenerator.rag.api.QueryProcessingContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Парсит стектрейс из запроса пользователя.
 * Извлекает тип исключения, сообщение, фреймы стека и определяет корневую причину.
 */
@Component
class StacktraceParsingStep : QueryStep {
    private val log = LoggerFactory.getLogger(javaClass)

    override val type: ProcessingStepType = ProcessingStepType.STACKTRACE_PARSING

    companion object {
        private val EXCEPTION_PATTERN = Regex("""([\w.$]+(?:Exception|Error|Throwable))\s*:\s*(.+)""")
        private val CAUSED_BY_PATTERN = Regex("""Caused\s+by\s*:\s*([\w.$]+(?:Exception|Error|Throwable))\s*:\s*(.*)""")
        private val FRAME_PATTERN = Regex("""at\s+([\w.$]+)\.([\w$<>]+)\(([^)]*?)(?::(\d+))?\)""")

        val LIBRARY_PREFIXES = setOf(
            "java.", "javax.", "jakarta.", "kotlin.", "kotlinx.",
            "org.springframework.", "org.apache.", "org.hibernate.", "com.zaxxer.",
            "io.netty.", "reactor.", "sun.", "jdk.", "org.postgresql.", "com.fasterxml.",
            "io.micrometer.", "org.slf4j.", "ch.qos.logback.",
        )
    }

    override fun execute(context: QueryProcessingContext): StepResult {
        val query = context.currentQuery

        val frames = parseFrames(query)
        if (frames.isEmpty()) {
            log.info("STACKTRACE_PARSING: no frames found, falling back to REWRITING")
            val updatedContext = context.addStep(
                ProcessingStep(
                    advisorName = "StacktraceParsingStep",
                    input = query,
                    output = "Стектрейс не распознан",
                    stepType = type,
                    status = ProcessingStepStatus.SUCCESS,
                ),
            )
            return StepResult(context = updatedContext, transitionKey = "PARSE_FAILED")
        }

        val appFrames = frames.filter { !isLibraryFrame(it) }
        val exceptionInfo = parseExceptionInfo(query)
        val rootCauseFrame = findRootCauseFrame(query, appFrames, frames)

        var updatedContext = context
            .setMetadata(QueryMetadataKeys.STACKTRACE_FRAMES, frames)
            .setMetadata(QueryMetadataKeys.STACKTRACE_APP_FRAMES, appFrames)
            .setMetadata(QueryMetadataKeys.STACKTRACE_EXCEPTION_TYPE, exceptionInfo.first)
            .setMetadata(QueryMetadataKeys.STACKTRACE_EXCEPTION_MESSAGE, exceptionInfo.second)
        if (rootCauseFrame != null) {
            updatedContext = updatedContext.setMetadata(QueryMetadataKeys.STACKTRACE_ROOT_CAUSE_FRAME, rootCauseFrame)
        }
        updatedContext = updatedContext.addStep(
                ProcessingStep(
                    advisorName = "StacktraceParsingStep",
                    input = query.take(200),
                    output = "Фреймов: ${frames.size}, приложения: ${appFrames.size}, исключение: ${exceptionInfo.first}",
                    stepType = type,
                    status = ProcessingStepStatus.SUCCESS,
                ),
            )

        log.info(
            "STACKTRACE_PARSING: frames={}, appFrames={}, exception={}, rootCause={}",
            frames.size, appFrames.size, exceptionInfo.first, rootCauseFrame?.methodName,
        )
        return StepResult(context = updatedContext, transitionKey = "SUCCESS")
    }

    internal fun parseFrames(text: String): List<StackFrame> {
        return FRAME_PATTERN.findAll(text).map { match ->
            val fullClassName = match.groupValues[1]
            val methodName = match.groupValues[2]
            val fileInfo = match.groupValues[3]
            val lineNumber = match.groupValues[4].toIntOrNull()

            StackFrame(
                fullClassName = fullClassName,
                className = fullClassName.substringAfterLast('.'),
                methodName = methodName,
                fileName = fileInfo.takeIf { it.isNotBlank() && !it.contains(':') } ?: fileInfo.substringBefore(':'),
                lineNumber = lineNumber,
            )
        }.toList()
    }

    internal fun parseExceptionInfo(text: String): Pair<String, String> {
        // Ищем последний "Caused by:" как корневую причину
        val causedByMatches = CAUSED_BY_PATTERN.findAll(text).toList()
        if (causedByMatches.isNotEmpty()) {
            val last = causedByMatches.last()
            return last.groupValues[1] to last.groupValues[2].trim()
        }

        // Если нет Caused by — ищем основное исключение
        val exceptionMatch = EXCEPTION_PATTERN.find(text)
        if (exceptionMatch != null) {
            return exceptionMatch.groupValues[1] to exceptionMatch.groupValues[2].trim()
        }

        return "" to ""
    }

    private fun findRootCauseFrame(
        text: String,
        appFrames: List<StackFrame>,
        allFrames: List<StackFrame>,
    ): StackFrame? {
        // Ищем последний блок "Caused by:" и первый app-фрейм в нём
        val causedByIndex = text.lastIndexOf("Caused by:")
        if (causedByIndex >= 0) {
            val causedBySection = text.substring(causedByIndex)
            val causedByFrames = parseFrames(causedBySection)
            val firstAppFrame = causedByFrames.firstOrNull { !isLibraryFrame(it) }
            if (firstAppFrame != null) return firstAppFrame
        }

        // Иначе — первый app-фрейм во всём стектрейсе
        return appFrames.firstOrNull() ?: allFrames.firstOrNull()
    }

    private fun isLibraryFrame(frame: StackFrame): Boolean {
        return LIBRARY_PREFIXES.any { frame.fullClassName.startsWith(it) }
    }

    override fun getTransitions(): Map<String, ProcessingStepType> {
        return linkedMapOf(
            "SUCCESS" to ProcessingStepType.STACKTRACE_ANALYSIS,
            "PARSE_FAILED" to ProcessingStepType.REWRITING,
        )
    }
}

/**
 * Один фрейм стектрейса.
 */
data class StackFrame(
    val fullClassName: String,
    val className: String,
    val methodName: String,
    val fileName: String?,
    val lineNumber: Int?,
)
