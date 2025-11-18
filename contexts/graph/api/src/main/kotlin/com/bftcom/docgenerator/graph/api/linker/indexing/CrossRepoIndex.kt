
package com.bftcom.docgenerator.graph.api.linker.indexing

import com.bftcom.docgenerator.domain.node.Node

interface CrossRepoIndex {
    fun resolveEndpoint(url: String): Node?

    fun resolveTopic(name: String): Node?

    fun resolveTable(name: String): Node?
}
