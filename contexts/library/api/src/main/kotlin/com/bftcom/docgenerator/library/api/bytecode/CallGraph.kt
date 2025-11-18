package com.bftcom.docgenerator.library.api.bytecode

/**
 * Граф вызовов методов (call graph).
 * Фаза 2: построение графа для подъема до родительских клиентов.
 */
data class CallGraph(
    /** Прямые вызовы: method -> set of methods it calls */
    val calls: Map<MethodId, Set<MethodId>> = emptyMap(),
    /** Обратные вызовы: method -> set of methods that call it */
    val reverseCalls: Map<MethodId, Set<MethodId>> = emptyMap(),
) {
    /**
     * Получить все методы, которые вызывают данный метод.
     */
    fun getCallers(methodId: MethodId): Set<MethodId> {
        return reverseCalls[methodId] ?: emptySet()
    }

    /**
     * Получить все методы, которые вызываются из данного метода.
     */
    fun getCallees(methodId: MethodId): Set<MethodId> {
        return calls[methodId] ?: emptySet()
    }

    /**
     * Построить обратный граф из прямого.
     */
    companion object {
        fun build(calls: Map<MethodId, Set<MethodId>>): CallGraph {
            val reverseCalls = mutableMapOf<MethodId, MutableSet<MethodId>>()

            for ((caller, callees) in calls) {
                for (callee in callees) {
                    reverseCalls.getOrPut(callee) { mutableSetOf() }.add(caller)
                }
            }

            return CallGraph(
                calls = calls,
                reverseCalls = reverseCalls,
            )
        }
    }
}

