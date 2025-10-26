package com.bftcom.docgenerator.ai.config

import org.apache.hc.client5.http.config.RequestConfig
import org.apache.hc.client5.http.impl.classic.HttpClients
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder
import org.apache.hc.core5.http.io.SocketConfig
import org.apache.hc.core5.util.TimeValue
import org.apache.hc.core5.util.Timeout
import org.springframework.boot.web.client.RestClientCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.ClientHttpRequestFactory
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory
import java.util.concurrent.TimeUnit

@Configuration
class RestClientConfig {

    /**
     * Бин №1.
     * Создаем саму "фабрику" HTTP-клиентов на Apache HC5.
     * Здесь вся магия пулинга и таймаутов.
     */
    @Bean
    fun httpComponentsClientHttpRequestFactory(): ClientHttpRequestFactory {

        // 1. Настраиваем пул соединений
        val connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
            // Настройка keep-alive (SO_KEEPALIVE)
            .setDefaultSocketConfig(
                SocketConfig.custom()
                    .setSoKeepAlive(true)
                    .build()
            )
            .setMaxConnTotal(200) // Макс. соединений всего
            .setMaxConnPerRoute(50) // Макс. соединений на один хост (например, на твой Ollama)
            .setConnectionTimeToLive(TimeValue.ofMinutes(10)) // Как долго соединение может жить в пуле
            .build()

        // 2. Настраиваем таймауты
        val requestConfig = RequestConfig.custom()
            // Таймаут на ПОДКЛЮЧЕНИЕ к Ollama
            .setConnectTimeout(Timeout.ofSeconds(5))
            // Таймаут на получение соединения ИЗ ПУЛА
            .setConnectionRequestTimeout(Timeout.ofSeconds(5))
            // !!! ГЛАВНЫЙ ФИКС !!!
            // Таймаут на ОЖИДАНИЕ ОТВЕТА (ReadTimeout)
            .setResponseTimeout(Timeout.ofMinutes(5)) // <-- Ставим 5 минут. Можешь ставить 10.
            .build()

        // 3. Собираем HTTP-клиент
        val httpClient = HttpClients.custom()
            .setConnectionManager(connectionManager)
            .setDefaultRequestConfig(requestConfig)
            // Периодически чистим "мертвые" соединения из пула
            .evictIdleConnections(TimeValue.ofSeconds(30))
            .build()

        // 4. Оборачиваем его в фабрику, понятную Spring
        return HttpComponentsClientHttpRequestFactory(httpClient)
    }

    /**
     * Бин №2.
     * Этот "кастомайзер" Spring Boot найдет сам.
     * Он возьмет RestClient.Builder, который использует Spring AI,
     * и "подсунет" ему нашу крутую фабрику.
     */
    @Bean
    fun aiRestClientCustomizer(factory: ClientHttpRequestFactory): RestClientCustomizer {
        return RestClientCustomizer { restClientBuilder ->
            restClientBuilder.requestFactory(factory)
        }
    }
}