package com.bftcom.docgenerator.graph.model

sealed class RawUsage {
    /**
     * Выражение с точкой: `receiver.member` или `receiver.member()`
     * @param receiver Текст слева от точки (e.g., "myService", "Utils", "com.example.Utils")
     * @param member Имя справа от точки (e.g., "doWork", "FIELD_NAME")
     * @param isCall Это вызов `()` или просто доступ к полю
     */
    data class Dot(
        val receiver: String,
        val member: String,
        val isCall: Boolean = true,
    ) : RawUsage()

    /**
     * Простое выражение: `doLocalWork()` или `MyClass()`
     * @param name Имя (e.g., "doLocalWork", "MyClass")
     * @param isCall Это вызов `()` (для `Simple` это почти всегда true)
     */
    data class Simple(
        val name: String,
        val isCall: Boolean,
    ) : RawUsage()
}
