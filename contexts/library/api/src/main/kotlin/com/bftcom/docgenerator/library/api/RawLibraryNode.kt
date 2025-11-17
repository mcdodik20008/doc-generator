package com.bftcom.docgenerator.library.api

import com.bftcom.docgenerator.domain.enums.Lang
import com.bftcom.docgenerator.domain.enums.NodeKind

/**
 * "Сырая" нода библиотеки, извлечённая из байткода.
 * Позже будет преобразована в LibraryNode и сохранена в БД.
 */
data class RawLibraryNode(
    /** FQN класса/метода/поля */
    val fqn: String,
    /** Короткое имя */
    val name: String,
    /** Пакет */
    val packageName: String?,
    /** Тип ноды (CLASS, METHOD, FIELD и т.д.) */
    val kind: NodeKind,
    /** Язык (обычно java, но может быть kotlin если есть kotlin-metadata) */
    val lang: Lang,
    /** Путь к файлу внутри jar (например, "com/example/MyClass.class") */
    val filePath: String?,
    /** Сигнатура метода (если это метод) */
    val signature: String?,
    /** Аннотации (FQN аннотаций) */
    val annotations: List<String> = emptyList(),
    /** Модификаторы (public, private, static, final и т.д.) */
    val modifiers: Set<String> = emptySet(),
    /** FQN родительского класса (если это вложенный класс или метод) */
    val parentFqn: String? = null,
    /** Метаданные (дополнительная информация) */
    val meta: Map<String, Any> = emptyMap(),
)
