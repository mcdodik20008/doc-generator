package com.bftcom.docgenerator.core.api

import com.bftcom.docgenerator.core.model.NodeDocDraft
import com.bftcom.docgenerator.core.model.NodeDocPatch
import com.bftcom.docgenerator.domain.nodedoc.NodeDoc

interface NodeDocPort {
    /** Upsert NodeDoc для ноды в указанной локали */
    fun upsert(
        nodeId: Long,
        draft: NodeDocDraft,
        sourceKind: String,
        modelName: String? = null,
    ): NodeDoc

    /** Merge: дописываем used_by (или иные блоки) */
    fun merge(
        nodeId: Long,
        patch: NodeDocPatch,
        sourceKind: String,
        modelName: String? = null,
    ): NodeDoc
}
