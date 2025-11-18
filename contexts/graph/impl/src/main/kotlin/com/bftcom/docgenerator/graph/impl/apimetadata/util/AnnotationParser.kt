package com.bftcom.docgenerator.graph.impl.apimetadata.util

import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtValueArgument

/**
 * Утилита для парсинга параметров аннотаций из Kotlin PSI.
 */
object AnnotationParser {
    /**
     * Извлечь строковое значение параметра аннотации.
     * @GetMapping("/api/users") -> "/api/users"
     * @GetMapping(value = "/api/users") -> "/api/users"
     * @GetMapping(path = "/api/users") -> "/api/users"
     */
    fun getStringValue(
        annotation: KtAnnotationEntry,
        paramName: String = "value",
    ): String? {
        val args = annotation.valueArguments
        if (args.isEmpty()) return null

        // Ищем по имени параметра (path, value и т.д.)
        val namedArg =
            args.firstOrNull {
                (it as? KtValueArgument)?.getArgumentName()?.asName?.asString() == paramName
            }
        if (namedArg != null) {
            return extractStringValue(namedArg as KtValueArgument)
        }

        // Если нет именованного аргумента, берем первый позиционный (value)
        val firstArg = args.firstOrNull() as? KtValueArgument
        return firstArg?.let { extractStringValue(it) }
    }

    /**
     * Извлечь массив строк из параметра аннотации.
     * @RequestMapping(consumes = ["application/json"]) -> ["application/json"]
     */
    fun getStringArrayValue(
        annotation: KtAnnotationEntry,
        paramName: String = "value",
    ): List<String>? {
        val arg = findArgument(annotation, paramName) ?: return null
        return extractStringArrayValue(arg)
    }

    /**
     * Извлечь массив строк из параметра, поддерживая несколько вариантов имен.
     * @GetMapping(path = ["/api", "/v1"]) -> ["/api", "/v1"]
     */
    fun getStringArrayValue(
        annotation: KtAnnotationEntry,
        paramNames: List<String>,
    ): List<String>? {
        for (name in paramNames) {
            val result = getStringArrayValue(annotation, name)
            if (result != null) return result
        }
        return null
    }

    /**
     * Извлечь одну строку, пробуя несколько имен параметров.
     */
    fun getStringValue(
        annotation: KtAnnotationEntry,
        paramNames: List<String>,
    ): String? {
        for (name in paramNames) {
            val result = getStringValue(annotation, name)
            if (result != null) return result
        }
        return null
    }

    private fun findArgument(
        annotation: KtAnnotationEntry,
        paramName: String,
    ): KtValueArgument? {
        val args = annotation.valueArguments
        val namedArg =
            args.firstOrNull {
                (it as? KtValueArgument)?.getArgumentName()?.asName?.asString() == paramName
            } as? KtValueArgument

        if (namedArg != null) return namedArg

        // Если ищем "value" и нет именованного, берем первый позиционный
        if (paramName == "value" && args.isNotEmpty()) {
            return args.firstOrNull() as? KtValueArgument
        }

        return null
    }

    private fun extractStringValue(arg: KtValueArgument): String? {
        val expr = arg.getArgumentExpression() ?: return null
        val text = expr.text.removeSurrounding("\"").removeSurrounding("'")
        return text.takeIf { it.isNotBlank() }
    }

    private fun extractStringArrayValue(arg: KtValueArgument): List<String>? {
        val expr = arg.getArgumentExpression() ?: return null
        val text = expr.text

        // Простой парсинг массива ["a", "b"] или arrayOf("a", "b")
        val matches = """["']([^"']+)["']""".toRegex().findAll(text)
        val values = matches.map { it.groupValues[1] }.toList()
        return values.takeIf { it.isNotEmpty() }
    }
}
