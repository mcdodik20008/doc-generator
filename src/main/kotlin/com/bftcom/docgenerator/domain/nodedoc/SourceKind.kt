package com.bftcom.docgenerator.domain.nodedoc

// В БД это TEXT с CHECK, маппим как enum-строку
enum class SourceKind { manual, llm, import }
