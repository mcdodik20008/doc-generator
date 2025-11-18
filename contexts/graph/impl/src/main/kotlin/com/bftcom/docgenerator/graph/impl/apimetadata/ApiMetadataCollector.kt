package com.bftcom.docgenerator.graph.impl.apimetadata

import com.bftcom.docgenerator.domain.enums.Lang
import com.bftcom.docgenerator.graph.api.apimetadata.ApiMetadata
import com.bftcom.docgenerator.graph.api.apimetadata.ApiMetadataExtractor
import com.bftcom.docgenerator.graph.api.model.rawdecl.RawFunction
import com.bftcom.docgenerator.graph.api.model.rawdecl.RawType
import com.bftcom.docgenerator.graph.api.nodekindextractor.NodeKindContext
import org.springframework.stereotype.Component

/**
 * Собирает метаданные API из всех доступных extractor'ов.
 */
@Component
class ApiMetadataCollector(
    private val extractors: List<ApiMetadataExtractor>,
) {
    /**
     * Извлечь метаданные API для функции/метода.
     * Пробует все extractor'ы и возвращает первый найденный результат.
     */
    fun extractFunctionMetadata(
        function: RawFunction,
        ownerType: RawType?,
        ctx: NodeKindContext,
    ): ApiMetadata? =
        extractors
            .asSequence()
            .filter { it.supports(ctx.lang) }
            .mapNotNull { it.extractFunctionMetadata(function, ownerType, ctx) }
            .firstOrNull()

    /**
     * Извлечь метаданные API для типа (класса).
     */
    fun extractTypeMetadata(
        type: RawType,
        ctx: NodeKindContext,
    ): ApiMetadata? =
        extractors
            .asSequence()
            .filter { it.supports(ctx.lang) }
            .mapNotNull { it.extractTypeMetadata(type, ctx) }
            .firstOrNull()
}
