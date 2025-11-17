package com.bftcom.docgenerator.library.api

import com.bftcom.docgenerator.domain.library.LibraryNode

/**
 * Индекс библиотек для быстрого поиска по FQN.
 * Используется на фазе построения графа приложения для резолва внешних типов/методов.
 */
interface LibraryIndex {
    /**
     * Находит LibraryNode по FQN (полное имя класса/метода).
     * @param fqn Полное имя, например "org.springframework.web.client.RestTemplate"
     * @return LibraryNode или null, если не найдено
     */
    fun findByFqn(fqn: String): LibraryNode?

    /**
     * Находит LibraryNode по FQN класса и имени метода.
     * @param classFqn FQN класса, например "org.springframework.web.client.RestTemplate"
     * @param methodName Имя метода, например "getForObject"
     * @return LibraryNode или null, если не найдено
     */
    fun findByClassAndMethod(classFqn: String, methodName: String): LibraryNode?

    /**
     * Проверяет, существует ли библиотека с указанной координатой.
     * @param coordinate Координата в формате "groupId:artifactId:version"
     * @return true, если библиотека уже проиндексирована
     */
    fun hasLibrary(coordinate: String): Boolean
}

