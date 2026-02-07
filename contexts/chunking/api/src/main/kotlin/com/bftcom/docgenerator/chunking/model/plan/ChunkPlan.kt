package com.bftcom.docgenerator.chunking.model.plan

import com.bftcom.docgenerator.domain.node.Node

data class ChunkPlan(
    val id: String, // детерминированный ID (nodeId+source+kind)
    val nodeId: Long, // прямая связь с нодой
    val source: String, // "code" | "doc"
    val kind: String, // "snippet" | "tech" | "public"
    val lang: String?, // "kotlin" | "java" | "ru" и т.д.
    val spanLines: IntRange?, // диапазон строк исходника, если применимо
    val title: String?, // обычно fqn
    val sectionPath: List<String>, // ["com","bftcom","Foo","bar"]
    val relations: List<RelationHint>, // лёгкие подсказки по связям
    val pipeline: PipelinePlan, // стадии, параметры и сервисная мета
    val node: Node, // ссылка на сам узел (нужен ниже по пайплайну)
)
