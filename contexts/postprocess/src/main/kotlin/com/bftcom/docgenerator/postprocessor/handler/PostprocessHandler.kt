package com.bftcom.docgenerator.postprocessor.handler

import com.bftcom.docgenerator.postprocessor.model.ChunkSnapshot
import com.bftcom.docgenerator.postprocessor.model.PartialMutation

interface PostprocessHandler {
    /** Хотим ли запускаться для этого чанка (без оглядки на порядок). */
    fun supports(s: ChunkSnapshot): Boolean

    /** Посчитать и вернуть только свои поля (можно ничего не вернуть). */
    fun produce(s: ChunkSnapshot): PartialMutation
}
