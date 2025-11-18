package com.bftcom.docgenerator.graph.api.library

import com.bftcom.docgenerator.domain.library.LibraryNode

/**
 * Индекс для быстрого поиска LibraryNode по FQN.
 * Используется при построении графа приложения для поиска методов библиотек.
 */
interface LibraryNodeIndex {
    /**
     * Находит LibraryNode по FQN метода.
     */
    fun findByMethodFqn(methodFqn: String): LibraryNode?

    /**
     * Находит LibraryNode по FQN класса и имени метода.
     */
    fun findByClassAndMethod(
        classFqn: String,
        methodName: String,
    ): LibraryNode?

    /**
     * Проверяет, является ли метод родительским клиентом.
     */
    fun isParentClient(methodFqn: String): Boolean
}
