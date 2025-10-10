package com.bftcom.docgenerator.zipper


import org.springframework.ai.document.Document
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.io.InputStream
import java.time.OffsetDateTime
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/** минималистичный matcher под **/
private object Glob {
    fun match(glob: String, path: String): Boolean {
        val g = glob.replace(".", "\\.")
            .replace("**/", "(.*/)?")
            .replace("**", ".*")
            .replace("*", "[^/]*")
        return Regex("^$g$").matches(path)
    }
}