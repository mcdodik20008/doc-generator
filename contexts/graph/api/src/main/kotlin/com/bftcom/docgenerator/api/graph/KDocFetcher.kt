package com.bftcom.docgenerator.api.graph

import com.bftcom.docgenerator.model.KDocParsed
import org.jetbrains.kotlin.psi.KtDeclaration

interface KDocFetcher {
    fun parseKDoc(decl: KtDeclaration): KDocParsed?

    fun toDocString(k: KDocParsed): String

    fun toMeta(k: KDocParsed?): Map<String, Any?>?
}
