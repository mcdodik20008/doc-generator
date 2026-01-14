package com.bftcom.docgenerator.domain.enums

/**
 * Тип узла графа кода/системы.
 */
enum class NodeKind {
    // Код/структура
    REPO,
    MODULE,
    PACKAGE,
    CLASS,
    INTERFACE,
    ENUM,
    RECORD,
    METHOD,
    FIELD,
    EXCEPTION,
    TEST,
    MAPPER,

    // Сервисный слой и интеграции
    SERVICE, // логическая сервисная единица (микросервис)
    ENDPOINT, // REST/gRPC/… входная точка
    CLIENT, // HTTP/WebClient/Feign/gRPC-клиент к внешнему сервису
    TOPIC, // Kafka/Rabbit/NATS тема/очередь
    JOB, // Scheduler/Worker/Batch
    DB_TABLE,
    DB_VIEW,
    DB_QUERY, // таблица/представление/запрос (MyBatis/JPA/SQL)
    SCHEMA, // контракт данных (Avro/JSON Schema/OpenAPI schema)
    CONFIG, // параметр конфигурации/фича-флаг/таймаут
    MIGRATION, // Liquibase/Flyway миграция
    ANNOTATION, // Статические аннотации
}
