package com.bftcom.docgenerator.graph.impl.node.builder.cache

import com.bftcom.docgenerator.domain.node.Node
import org.slf4j.LoggerFactory

/**
 * Кэш существующих узлов для избежания N+1 запросов.
 */
class NodeCache {
    private val log = LoggerFactory.getLogger(javaClass)
    private val cache = mutableMapOf<String, Node?>()

    /**
     * Получает узел из кэша или вычисляет его через функцию.
     * @param fqn Полное имя узла
     * @param compute Функция для вычисления узла, если его нет в кэше
     * @return Узел или null
     */
    fun getOrCompute(fqn: String, compute: () -> Node?): Node? {
        return cache.getOrPut(fqn, compute)
    }

    /**
     * Обновляет кэш для указанного FQN.
     * @param fqn Полное имя узла
     * @param node Узел для кэширования
     */
    fun put(fqn: String, node: Node) {
        cache[fqn] = node
    }

    /**
     * Очищает кэш.
     */
    fun clear() {
        val size = cache.size
        cache.clear()
        log.debug("Cleared node cache: removed {} entries", size)
    }

    /**
     * Возвращает размер кэша.
     */
    fun size(): Int = cache.size
}

