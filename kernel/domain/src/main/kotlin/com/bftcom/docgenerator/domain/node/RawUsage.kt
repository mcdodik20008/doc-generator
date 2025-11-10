package com.bftcom.docgenerator.domain.node

/**
 * Сырой вызов или упоминание внутри тела функции.
 *
 * Используется для анализа связей на уровне исходного кода (до Linker).
 */
sealed class RawUsage {
    /**
     * Вызов или обращение вида `receiver.member(...)`.
     *
     * @param receiver текстовая часть до точки
     * @param member имя вызываемого метода или свойства
     * @param isCall true, если это именно вызов `()`, а не просто обращение
     */
    data class Dot(
        val receiver: String,
        val member: String,
        val isCall: Boolean = true
    ) : RawUsage()

    /**
     * Простое обращение или вызов без квалификатора,
     * например `foo()` или `print`.
     */
    data class Simple(
        val name: String,
        val isCall: Boolean
    ) : RawUsage()
}