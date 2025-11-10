package com.bftcom.docgenerator.graph.api

import com.bftcom.docgenerator.graph.api.model.rawdecl.RawDecl
import com.bftcom.docgenerator.graph.api.model.rawdecl.RawField
import com.bftcom.docgenerator.graph.api.model.rawdecl.RawFileUnit
import com.bftcom.docgenerator.graph.api.model.rawdecl.RawFunction
import com.bftcom.docgenerator.graph.api.model.rawdecl.RawPackage
import com.bftcom.docgenerator.graph.api.model.rawdecl.RawType

interface SourceVisitor {
    fun onDecl(raw: RawDecl) {
        when (raw) {
            is RawFileUnit -> onFile(raw)
            is RawPackage  -> onPackage(raw)
            is RawType     -> onType(raw)
            is RawField    -> onField(raw)
            is RawFunction -> onFunction(raw)
        }
    }

    fun onFile(unit: RawFileUnit) {}
    fun onPackage(decl: RawPackage) {}
    fun onType(decl: RawType) {}
    fun onField(decl: RawField) {}
    fun onFunction(decl: RawFunction) {}
}
