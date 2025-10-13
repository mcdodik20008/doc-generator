package com.bftcom.docgenerator.core.api

interface GraphPort {
    /** Узлы в порядке bottom-up (листья -> корни) */
    fun topoOrder(): List<Long>

    /** Узлы в порядке top-down (корни -> листья) */
    fun reverseTopoOrder(): List<Long>

    /** Кого N использует (рёбра: N -> dep) */
    fun dependenciesOf(nodeId: Long): List<Long>

    /** Кто использует N (рёбра: user -> N) */
    fun dependentsOf(nodeId: Long): List<Long>
}
