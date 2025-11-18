package com.bftcom.docgenerator.graph.api.apimetadata

import com.bftcom.docgenerator.domain.enums.Lang
import com.bftcom.docgenerator.graph.api.model.rawdecl.RawFunction
import com.bftcom.docgenerator.graph.api.model.rawdecl.RawType
import com.bftcom.docgenerator.graph.api.nodekindextractor.NodeKindContext

/**
 * Извлекает метаданные API из аннотаций и кода.
 * Работает независимо от классификаторов - только извлекает параметры.
 */
interface ApiMetadataExtractor {
    /**
     * Уникальный идентификатор extractor'а
     */
    fun id(): String

    /**
     * Поддерживаемый язык
     */
    fun supports(lang: Lang): Boolean = true

    /**
     * Извлечь метаданные из функции/метода.
     * Вызывается для методов с аннотациями API (например, @GetMapping, @KafkaListener и т.д.)
     */
    fun extractFunctionMetadata(
        function: RawFunction,
        ownerType: RawType?,
        ctx: NodeKindContext,
    ): ApiMetadata? = null

    /**
     * Извлечь метаданные из типа (класса).
     * Вызывается для классов с аннотациями API (например, @RestController, @GrpcService и т.д.)
     */
    fun extractTypeMetadata(
        type: RawType,
        ctx: NodeKindContext,
    ): ApiMetadata? = null
}
