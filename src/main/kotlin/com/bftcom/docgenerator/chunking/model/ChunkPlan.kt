package com.bftcom.docgenerator.chunking.model

import com.bftcom.docgenerator.domain.node.Node

data class ChunkPlan(
    val id: String,                     // детерминированный ID (nodeId+source+kind)
    val nodeId: Long,                   // прямая связь с нодой
    val source: String,                 // "code" | "doc"
    val kind: String,                   // "snippet" | "explanation"
    val lang: String?,                  // "kotlin" | "java" | "ru" и т.д.
    val spanLines: IntRange?,           // диапазон строк исходника, если применимо
    val title: String?,                 // обычно fqn
    val sectionPath: List<String>,      // ["com","bftcom","Foo","bar"]
    val relations: List<RelationHint>,  // лёгкие подсказки по связям
    val pipeline: PipelinePlan,         // стадии, параметры и сервисная мета
    val node: Node                      // ссылка на сам узел (нужен ниже по пайплайну)
)

data class RelationHint(
    val kind: String,                   // "CALLS", "READS", ...
    val dstNodeId: Long,
    val confidence: Double = 0.7
)

data class PipelinePlan(
    val stages: List<String>,           // ["extract-snippet","summarize","embed",...]
    val params: Map<String, Any> = emptyMap(),  // лёгкие параметры (без тяжёлых данных)
    val service: ServiceMeta = ServiceMeta()
)

data class ServiceMeta(
    val strategy: String = "per-node",
    val priority: Int = 0,
    val correlationId: String? = null,
    val traceId: String? = null
)