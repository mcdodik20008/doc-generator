package com.bftcom.docgenerator.graph.impl.declhandler

import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.graph.api.declhandler.BaseDeclPlanner
import com.bftcom.docgenerator.graph.api.declhandler.DeclCmd
import com.bftcom.docgenerator.graph.api.declhandler.EnsurePackageCmd
import com.bftcom.docgenerator.graph.api.declhandler.UpsertTypeCmd
import com.bftcom.docgenerator.graph.api.model.rawdecl.RawType
import org.springframework.stereotype.Component
import kotlin.reflect.KClass

@Component
class TypePlanner : BaseDeclPlanner<RawType>(RawType::class as KClass<RawType>) {
    override fun plan(raw: RawType): List<DeclCmd> {
        val baseKind = when (raw.kindRepr) {
            "interface" -> NodeKind.INTERFACE
            "enum"      -> NodeKind.ENUM
            "record"    -> NodeKind.RECORD
            "object"    -> NodeKind.CLASS
            else        -> NodeKind.CLASS
        }
        return buildList {
            raw.pkgFqn?.let { add(EnsurePackageCmd(it, raw.filePath)) }
            add(UpsertTypeCmd(raw, baseKind))
        }
    }
}