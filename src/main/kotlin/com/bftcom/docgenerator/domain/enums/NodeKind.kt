package com.bftcom.docgenerator.domain.enums

/**
 * Тип узла графа кода/системы.
 *
 * - MODULE — логический модуль/артефакт (Gradle-модуль, JAR)
 * - PACKAGE — пакет / namespace
 * - CLASS / INTERFACE / ENUM / RECORD — языковые сущности
 * - FIELD — поле сущности
 * - METHOD — метод / функция
 * - ENDPOINT — HTTP/gRPC endpoint
 * - TOPIC — брокерское событие (Kafka, NATS, RabbitMQ)
 * - DBTABLE — таблица БД
 * - MIGRATION — миграция схемы
 * - CONFIG — конфигурационный объект / файл
 * - JOB — плановая задача / джоб
 */
enum class NodeKind {
    MODULE,
    PACKAGE,
    CLASS,
    INTERFACE,
    ENUM,
    RECORD,
    FIELD,
    METHOD,
    ENDPOINT,
    TOPIC,
    DBTABLE,
    MIGRATION,
    CONFIG,
    JOB
}