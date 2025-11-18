package com.bftcom.docgenerator.library.api

/**
 * Координаты библиотеки (Maven/Gradle артефакт).
 */
data class LibraryCoordinate(
    val groupId: String,
    val artifactId: String,
    val version: String,
) {
    /**
     * Полная координата в формате groupId:artifactId:version
     */
    val coordinate: String
        get() = "$groupId:$artifactId:$version"

    companion object {
        /**
         * Парсит координату из строки формата "groupId:artifactId:version"
         */
        fun parse(coordinate: String): LibraryCoordinate? {
            val parts = coordinate.split(":")
            if (parts.size != 3) return null
            return LibraryCoordinate(
                groupId = parts[0],
                artifactId = parts[1],
                version = parts[2],
            )
        }
    }
}
