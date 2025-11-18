package com.bftcom.docgenerator.library.api.bytecode

/**
 * Идентификатор метода в байткоде.
 * Используется для построения call graph.
 */
data class MethodId(
    /** Internal name класса (например, "com/example/Foo") */
    val owner: String,
    /** Имя метода */
    val name: String,
    /** Дескриптор метода (типы аргументов и возвращаемого значения) */
    val descriptor: String,
) {
    /** FQN класса (com.example.Foo) */
    val ownerFqn: String = owner.replace('/', '.')

    /** Полный идентификатор для сравнения */
    val fullId: String = "$owner.$name$descriptor"

    override fun toString(): String = "$ownerFqn.$name$descriptor"
}

