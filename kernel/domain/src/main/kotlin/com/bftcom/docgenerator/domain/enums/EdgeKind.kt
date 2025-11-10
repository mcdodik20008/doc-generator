package com.bftcom.docgenerator.domain.enums

/**
 * Семантика ребра графа.
 */
enum class EdgeKind {
    // Статика и ООП
    CONTAINS,            // REPO→MODULE, MODULE→PACKAGE, CLASS→METHOD/FIELD и т.д.
    DEPENDS_ON,          // модуль/класс зависит от (импорт/библиотека)
    IMPLEMENTS,          // CLASS→INTERFACE
    INHERITS,
    EXTENDS,             // CLASS→CLASS, INTERFACE→INTERFACE
    OVERRIDES,           // METHOD→METHOD
    ANNOTATED_WITH,      // CLASS/METHOD/… → АННОТАЦИЯ (как узел CLASS)

    // Вызовы в коде (внутри процесса)
    CALLS,               // DEPRECATED
    CALLS_CODE,          // METHOD→METHOD
    THROWS,              // METHOD→EXCEPTION
    LOCKS,               // METHOD→RESOURCE (если моделируете ресурс как CLASS/FIELD)

    // Сетевые и интеграционные взаимодействия
    CALLS_HTTP,          // CLIENT/SERVICE → ENDPOINT (HTTP/GraphQL/WS)
    CALLS_GRPC,          // CLIENT/SERVICE → ENDPOINT (gRPC)
    PRODUCES,            // METHOD/ENDPOINT → TOPIC
    CONSUMES,            // WORKER/JOB/ENDPOINT → TOPIC
    QUERIES,             // METHOD/SERVICE → DB_QUERY
    READS,               // METHOD/SERVICE → DB_TABLE/DB_VIEW
    WRITES,              // METHOD/SERVICE → DB_TABLE
    CONTRACTS_WITH,      // ENDPOINT/TOPIC ↔ SCHEMA (JSON/Avro contract)
    CONFIGURES,          // CONFIG → SERVICE/CLIENT/ENDPOINT (таймауты/ретраи/фичи)
    CIRCUIT_BREAKER_TO,  // CLIENT/ENDPOINT → SERVICE (логический CB-линк)
    RETRIES_TO,          // CLIENT/ENDPOINT → SERVICE
    TIMEOUTS_TO,         // CONFIG/CLIENT/ENDPOINT → SERVICE
}
