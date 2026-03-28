package com.bftcom.docgenerator.graph.impl.config

/**
 * Тип обнаруженной инфраструктурной интеграции.
 */
enum class InfraIntegrationType {
    HTTP,
    DATABASE,
    KAFKA,
    RABBIT,
    CAMUNDA,
    AUTH,
    CONFIG_SERVER,
}

/**
 * Описание обнаруженной интеграции из YAML-конфигурации.
 */
data class DetectedIntegration(
    /** Тип интеграции */
    val type: InfraIntegrationType,
    /** Общий префикс ключей конфигурации, определяющий группу */
    val groupKey: String,
    /** Все свойства, относящиеся к этой интеграции */
    val properties: Map<String, String>,
    /** URL/URI по умолчанию (если найден) */
    val defaultUrl: String?,
    /** Переменная окружения (если значение ссылается на ${VAR}) */
    val envVar: String?,
)

/**
 * Определяет интеграционные зависимости по ключам и значениям YAML-конфигурации.
 * Чистая логика без side-effects — удобно тестировать.
 */
object IntegrationDetector {
    private val URL_VALUE_PATTERN = Regex("^https?://|^\\$\\{.*}$", RegexOption.IGNORE_CASE)
    private val ENV_VAR_PATTERN = Regex("\\$\\{([^}:]+)(?::([^}]*))?}")

    /**
     * Обнаруживает все интеграционные зависимости в плоской карте YAML-свойств.
     * @param flatProps карта "dot.separated.key" → "value"
     * @return список обнаруженных интеграций
     */
    fun detect(flatProps: Map<String, String>): List<DetectedIntegration> {
        val result = mutableListOf<DetectedIntegration>()
        val consumed = mutableSetOf<String>()

        // Порядок важен: сначала более специфичные паттерны
        result += detectDatabase(flatProps, consumed)
        result += detectKafka(flatProps, consumed)
        result += detectRabbit(flatProps, consumed)
        result += detectCamunda(flatProps, consumed)
        result += detectAuth(flatProps, consumed)
        result += detectConfigServer(flatProps, consumed)
        result += detectHttp(flatProps, consumed)

        return result
    }

    // --- Database ---

    private fun detectDatabase(
        props: Map<String, String>,
        consumed: MutableSet<String>,
    ): List<DetectedIntegration> {
        val result = mutableListOf<DetectedIntegration>()

        // spring.datasource.* / spring.r2dbc.* / *.datasource.*
        val dbKeys =
            props.keys.filter { key ->
                key !in consumed && (
                    key.startsWith("spring.datasource.") ||
                        key.startsWith("spring.r2dbc.") ||
                        key.contains(".datasource.")
                )
            }

        val groups = groupByPrefix(dbKeys, props)
        for ((group, groupProps) in groups) {
            val urlKey = groupProps.keys.firstOrNull { it.endsWith(".url") || it.endsWith(".jdbc-url") }
            val urlValue = urlKey?.let { groupProps[it] }
            if (urlValue != null && (urlValue.contains("jdbc:") || urlValue.contains("r2dbc:"))) {
                consumed += groupProps.keys
                val (envVar, defaultUrl) = extractEnvAndDefault(urlValue)
                result +=
                    DetectedIntegration(
                        type = InfraIntegrationType.DATABASE,
                        groupKey = group,
                        properties = groupProps,
                        defaultUrl = defaultUrl ?: urlValue,
                        envVar = envVar,
                    )
            }
        }
        return result
    }

    // --- Kafka ---

    private fun detectKafka(
        props: Map<String, String>,
        consumed: MutableSet<String>,
    ): List<DetectedIntegration> {
        val result = mutableListOf<DetectedIntegration>()

        // Все spring.kafka.* свойства — одна группа
        val springKafkaKeys = props.keys.filter { it !in consumed && it.startsWith("spring.kafka.") }
        if (springKafkaKeys.isNotEmpty()) {
            val groupProps = springKafkaKeys.associateWith { props[it]!! }
            consumed += springKafkaKeys
            val serversKey = groupProps.keys.firstOrNull { it.endsWith(".bootstrap-servers") }
            val serversValue = serversKey?.let { groupProps[it] }
            val (envVar, defaultUrl) = if (serversValue != null) extractEnvAndDefault(serversValue) else (null to null)

            result +=
                DetectedIntegration(
                    type = InfraIntegrationType.KAFKA,
                    groupKey = "spring.kafka",
                    properties = groupProps,
                    defaultUrl = defaultUrl ?: serversValue,
                    envVar = envVar,
                )
        }

        // Кастомные *.bootstrap-servers вне spring.kafka
        val customKafkaKeys =
            props.keys.filter { key ->
                key !in consumed && key.endsWith(".bootstrap-servers") && !key.startsWith("spring.kafka.")
            }
        for (key in customKafkaKeys) {
            val groupKey = key.substringBeforeLast('.')
            val groupProps = props.filter { (k, _) -> k !in consumed && k.startsWith("$groupKey.") }
            consumed += groupProps.keys
            val serversValue = props[key]
            val (envVar, defaultUrl) = if (serversValue != null) extractEnvAndDefault(serversValue) else (null to null)

            result +=
                DetectedIntegration(
                    type = InfraIntegrationType.KAFKA,
                    groupKey = groupKey,
                    properties = groupProps,
                    defaultUrl = defaultUrl ?: serversValue,
                    envVar = envVar,
                )
        }

        return result
    }

    // --- Rabbit ---

    private fun detectRabbit(
        props: Map<String, String>,
        consumed: MutableSet<String>,
    ): List<DetectedIntegration> {
        val result = mutableListOf<DetectedIntegration>()

        val rabbitKeys =
            props.keys.filter { key ->
                key !in consumed && (
                    key.startsWith("spring.rabbitmq.") ||
                        key.contains("rabbit", ignoreCase = true)
                )
            }

        val groups = groupByPrefix(rabbitKeys, props)
        for ((group, groupProps) in groups) {
            // Для кастомных rabbit-настроек требуем host + (exchange или queue)
            val hasHost = groupProps.keys.any { it.endsWith(".host") }
            val hasExchangeOrQueue =
                groupProps.keys.any {
                    it.endsWith(".exchange") || it.endsWith(".queue") || it.endsWith(".routing-key")
                }
            val isSpringRabbit = group.startsWith("spring.rabbitmq")

            if (isSpringRabbit || (hasHost && hasExchangeOrQueue)) {
                consumed += groupProps.keys
                val hostKey = groupProps.keys.firstOrNull { it.endsWith(".host") }
                val hostValue = hostKey?.let { groupProps[it] }
                val (envVar, defaultUrl) = if (hostValue != null) extractEnvAndDefault(hostValue) else (null to null)

                result +=
                    DetectedIntegration(
                        type = InfraIntegrationType.RABBIT,
                        groupKey = group,
                        properties = groupProps,
                        defaultUrl = defaultUrl ?: hostValue,
                        envVar = envVar,
                    )
            }
        }
        return result
    }

    // --- Camunda ---

    private fun detectCamunda(
        props: Map<String, String>,
        consumed: MutableSet<String>,
    ): List<DetectedIntegration> {
        val result = mutableListOf<DetectedIntegration>()

        val camundaKeys = props.keys.filter { it !in consumed && it.startsWith("camunda.") }
        if (camundaKeys.isNotEmpty()) {
            val groupProps = camundaKeys.associateWith { props[it]!! }
            consumed += camundaKeys
            val urlKey = groupProps.keys.firstOrNull { it.endsWith(".base-url") || it.endsWith(".url") }
            val urlValue = urlKey?.let { groupProps[it] }
            val (envVar, defaultUrl) = if (urlValue != null) extractEnvAndDefault(urlValue) else (null to null)

            result +=
                DetectedIntegration(
                    type = InfraIntegrationType.CAMUNDA,
                    groupKey = "camunda",
                    properties = groupProps,
                    defaultUrl = defaultUrl ?: urlValue,
                    envVar = envVar,
                )
        }
        return result
    }

    // --- Auth (OAuth2) ---

    private fun detectAuth(
        props: Map<String, String>,
        consumed: MutableSet<String>,
    ): List<DetectedIntegration> {
        val result = mutableListOf<DetectedIntegration>()

        val authKeys = props.keys.filter { it !in consumed && it.startsWith("spring.security.oauth2.") }
        if (authKeys.isNotEmpty()) {
            val groups = groupByPrefix(authKeys, props)
            for ((group, groupProps) in groups) {
                consumed += groupProps.keys
                val urlKey =
                    groupProps.keys.firstOrNull {
                        it.endsWith(".issuer-uri") || it.endsWith(".token-uri") || it.endsWith(".authorization-uri")
                    }
                val urlValue = urlKey?.let { groupProps[it] }
                val (envVar, defaultUrl) = if (urlValue != null) extractEnvAndDefault(urlValue) else (null to null)

                result +=
                    DetectedIntegration(
                        type = InfraIntegrationType.AUTH,
                        groupKey = group,
                        properties = groupProps,
                        defaultUrl = defaultUrl ?: urlValue,
                        envVar = envVar,
                    )
            }
        }
        return result
    }

    // --- Config Server ---

    private fun detectConfigServer(
        props: Map<String, String>,
        consumed: MutableSet<String>,
    ): List<DetectedIntegration> {
        val result = mutableListOf<DetectedIntegration>()

        val configKeys = props.keys.filter { it !in consumed && it.startsWith("spring.cloud.config.") }
        if (configKeys.isNotEmpty()) {
            val groupProps = configKeys.associateWith { props[it]!! }
            consumed += configKeys
            val urlKey = groupProps.keys.firstOrNull { it.endsWith(".uri") }
            val urlValue = urlKey?.let { groupProps[it] }
            val (envVar, defaultUrl) = if (urlValue != null) extractEnvAndDefault(urlValue) else (null to null)

            result +=
                DetectedIntegration(
                    type = InfraIntegrationType.CONFIG_SERVER,
                    groupKey = "spring.cloud.config",
                    properties = groupProps,
                    defaultUrl = defaultUrl ?: urlValue,
                    envVar = envVar,
                )
        }
        return result
    }

    // --- HTTP (generic URL/URI properties) ---

    private fun detectHttp(
        props: Map<String, String>,
        consumed: MutableSet<String>,
    ): List<DetectedIntegration> {
        val result = mutableListOf<DetectedIntegration>()

        // Ищем ключи, которые выглядят как URL/URI-настройки
        val urlSuffixes = setOf(".url", ".uri", ".rest-uri", ".base-url", ".api-url", ".service-url")
        val httpLeafKeys =
            props.keys
                .filter { key ->
                    key !in consumed && urlSuffixes.any { suffix -> key.endsWith(suffix, ignoreCase = true) }
                }.filter { key ->
                    val value = props[key] ?: ""
                    looksLikeUrl(value)
                }

        // Группируем по родительскому префиксу
        for (leafKey in httpLeafKeys) {
            val groupKey = leafKey.substringBeforeLast('.')
            if (groupKey in consumed) continue

            // Собираем все свойства группы
            val groupProps =
                props.filter { (k, _) ->
                    k !in consumed && k.startsWith("$groupKey.")
                }

            consumed += groupProps.keys
            val urlValue = props[leafKey] ?: ""
            val (envVar, defaultUrl) = extractEnvAndDefault(urlValue)

            result +=
                DetectedIntegration(
                    type = InfraIntegrationType.HTTP,
                    groupKey = groupKey,
                    properties = groupProps,
                    defaultUrl = defaultUrl ?: urlValue,
                    envVar = envVar,
                )
        }
        return result
    }

    // --- Utility ---

    /**
     * Извлекает имя переменной окружения и дефолтное значение из `${VAR:default}`.
     */
    fun extractEnvAndDefault(value: String): Pair<String?, String?> {
        val match = ENV_VAR_PATTERN.find(value) ?: return null to null
        val envVar = match.groupValues[1].takeIf { it.isNotBlank() }
        val default = match.groupValues[2].takeIf { it.isNotBlank() }
        return envVar to default
    }

    /**
     * Разрешает placeholder `${VAR:default}` → default-значение (если есть).
     */
    fun resolveDefault(value: String): String =
        ENV_VAR_PATTERN.replace(value) { match ->
            match.groupValues[2].takeIf { it.isNotBlank() } ?: match.value
        }

    private fun looksLikeUrl(value: String): Boolean {
        if (value.isBlank()) return false
        // Прямой URL
        if (value.startsWith("http://") || value.startsWith("https://")) return true
        // Placeholder с URL-дефолтом
        val match = ENV_VAR_PATTERN.find(value)
        if (match != null) {
            val default = match.groupValues[2]
            if (default.startsWith("http://") || default.startsWith("https://")) return true
            // Placeholder без дефолта — всё равно считаем URL-ом, т.к. ключ — url/uri
            return true
        }
        return false
    }

    /**
     * Группирует ключи по общему префиксу (ищет "семантическую группу").
     * Для `rr.ups-client.api-url` группа = `rr.ups-client`.
     * Для `spring.datasource.url` группа = `spring.datasource`.
     */
    private fun groupByPrefix(
        keys: Collection<String>,
        allProps: Map<String, String>,
    ): Map<String, Map<String, String>> {
        val groups = mutableMapOf<String, MutableMap<String, String>>()

        for (key in keys) {
            val prefix = key.substringBeforeLast('.')
            // Ищем самый глубокий общий префикс, который содержит несколько свойств
            val group = findGroupPrefix(prefix, allProps)
            groups.getOrPut(group) { mutableMapOf() }[key] = allProps[key] ?: ""
        }

        return groups
    }

    private fun findGroupPrefix(
        prefix: String,
        allProps: Map<String, String>,
    ): String {
        // Поднимаемся вверх, пока у нас есть хотя бы 2 свойства
        var current = prefix
        while (current.contains('.')) {
            val count = allProps.keys.count { it.startsWith("$current.") }
            if (count >= 2) return current

            current = current.substringBeforeLast('.')
        }
        return prefix
    }

    /**
     * Генерирует читаемое имя из groupKey.
     * `rr.ups-client` → `ups-client`
     * `spring.datasource` → `datasource`
     * `rr.unsi.client` → `unsi-client`
     */
    fun readableName(
        groupKey: String,
        type: InfraIntegrationType,
    ): String {
        val stripped =
            groupKey
                .removePrefix("rr.")
                .removePrefix("spring.")
                .removePrefix("spring.cloud.")

        val name =
            when {
                stripped.contains('.') -> {
                    val parts = stripped.split('.')
                    // Берём наиболее информативные части (пропуская generic слова)
                    val genericWords = setOf("client", "settings", "config", "configuration", "properties")
                    val meaningful = parts.filter { it.lowercase() !in genericWords }
                    if (meaningful.isNotEmpty()) meaningful.joinToString("-") else parts.joinToString("-")
                }

                else -> {
                    stripped
                }
            }

        return "$name (${type.name})"
    }
}
