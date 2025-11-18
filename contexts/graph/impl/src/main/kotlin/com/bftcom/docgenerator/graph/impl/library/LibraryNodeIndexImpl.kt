package com.bftcom.docgenerator.graph.impl.library

import com.bftcom.docgenerator.db.LibraryNodeRepository
import com.bftcom.docgenerator.domain.library.LibraryNode
import com.bftcom.docgenerator.graph.api.library.LibraryNodeIndex
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Реализация индекса LibraryNode.
 * Кэширует все LibraryNode в памяти для быстрого поиска.
 */
@Component
class LibraryNodeIndexImpl(
    private val libraryNodeRepo: LibraryNodeRepository,
) : LibraryNodeIndex {
    private val log = LoggerFactory.getLogger(javaClass)
    
    // Индексы для быстрого поиска
    private val byMethodFqn = mutableMapOf<String, LibraryNode>()
    private val byClassAndMethod = mutableMapOf<String, LibraryNode>()
    
    @PostConstruct
    fun buildIndex() {
        log.info("Building LibraryNode index...")
        val allNodes = libraryNodeRepo.findAll()
        
        for (node in allNodes) {
            if (node.kind.name == "METHOD") {
                // Индекс по полному FQN метода
                byMethodFqn[node.fqn] = node
                
                // Индекс по классу и методу
                val lastDot = node.fqn.lastIndexOf('.')
                if (lastDot > 0) {
                    val classFqn = node.fqn.substring(0, lastDot)
                    val methodName = node.fqn.substring(lastDot + 1)
                    val key = "$classFqn.$methodName"
                    byClassAndMethod[key] = node
                }
            }
        }
        
        log.info("LibraryNode index built: {} methods indexed", byMethodFqn.size)
    }
    
    override fun findByMethodFqn(methodFqn: String): LibraryNode? {
        return byMethodFqn[methodFqn]
    }
    
    override fun findByClassAndMethod(classFqn: String, methodName: String): LibraryNode? {
        val key = "$classFqn.$methodName"
        return byClassAndMethod[key]
    }
    
    @Suppress("UNCHECKED_CAST")
    override fun isParentClient(methodFqn: String): Boolean {
        val node = findByMethodFqn(methodFqn) ?: return false
        val integrationMeta = (node.meta["integrationAnalysis"] as? Map<String, Any>)
        return integrationMeta?.get("isParentClient") as? Boolean ?: false
    }
}

