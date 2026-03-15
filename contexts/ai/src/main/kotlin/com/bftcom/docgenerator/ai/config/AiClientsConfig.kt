package com.bftcom.docgenerator.ai.config

import com.bftcom.docgenerator.ai.advisor.ChatClientLoggingAdvisor
import com.bftcom.docgenerator.ai.props.AiClientsProperties
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor
import org.springframework.ai.chat.memory.ChatMemory
import org.springframework.ai.chat.memory.ChatMemoryRepository
import org.springframework.ai.chat.memory.MessageWindowChatMemory
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.embedding.EmbeddingModel
import org.springframework.ai.openai.OpenAiChatModel
import org.springframework.ai.openai.OpenAiChatOptions
import org.springframework.ai.openai.api.OpenAiApi
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.retry.support.RetryTemplate

@Configuration
class AiClientsConfig {
    /**
     * Явный RetryTemplate с одной попыткой (без ретраев).
     * Перекрывает автоконфигурацию Spring AI, которая игнорирует spring.ai.retry.max-attempts
     * и использует RetryUtils.DEFAULT_RETRY_TEMPLATE (10 попыток) внутри OpenAiChatModel.Builder.
     * Resilience4j (ResilientExecutor) управляет ретраями самостоятельно.
     */
    @Bean
    @Primary
    fun noRetryTemplate(): RetryTemplate =
        RetryTemplate.builder()
            .maxAttempts(1)
            .noBackoff()
            .build()

    /** ChatModel для coder с собственными дефолтными опциями */
    @Qualifier("coderChatModel")
    @Bean(name = ["coderChatModel"])
    fun coderChatModel(
            ollamaApi: OpenAiApi,
            props: AiClientsProperties,
            retryTemplate: RetryTemplate,
    ): OpenAiChatModel {
        val p = props.coder
        val options =
                OpenAiChatOptions.builder()
                        .model(p.model)
                        .temperature(p.temperature)
                        .topP(p.topP)
                        .seed(p.seed)
                        .build()
        return OpenAiChatModel.builder()
                .openAiApi(ollamaApi)
                .defaultOptions(options)
                .retryTemplate(retryTemplate)
                .build()
    }

    /** ChatModel для talker с собственными опциями */
    @Qualifier("talkerChatModel")
    @Bean(name = ["talkerChatModel"])
    fun talkerChatModel(
            ollamaApi: OpenAiApi,
            props: AiClientsProperties,
            retryTemplate: RetryTemplate,
    ): OpenAiChatModel {
        val p = props.talker
        val options =
                OpenAiChatOptions.builder()
                        .model(p.model)
                        .temperature(p.temperature)
                        .topP(p.topP)
                        .seed(p.seed)
                        .build()
        return OpenAiChatModel.builder()
                .openAiApi(ollamaApi)
                .defaultOptions(options)
                .retryTemplate(retryTemplate)
                .build()
    }

    /**
     * ChatClient'ы без defaultSystem — каждый вызывающий клиент
     * (OllamaCoderClient, SummaryClient, NodeDocDigestClient, OllamaTalkerClient)
     * всегда передаёт собственный .system(...) с task-specific промптом.
     * defaultSystem только дублировался и раздувал контекст.
     */
    @Bean
    @Primary
    @Qualifier("coderChatClient")
    fun coderChatClient(
            @Qualifier("coderChatModel") coderChatModel: ChatModel,
            loggingAdvisor: ChatClientLoggingAdvisor,
    ): ChatClient =
            ChatClient.builder(coderChatModel)
                    .defaultAdvisors(loggingAdvisor)
                    .build()

    @Bean
    @Qualifier("talkerChatClient")
    fun talkerChatClient(
            @Qualifier("talkerChatModel") talkerChatModel: ChatModel,
            loggingAdvisor: ChatClientLoggingAdvisor,
    ): ChatClient =
            ChatClient.builder(talkerChatModel)
                    .defaultAdvisors(loggingAdvisor)
                    .build()

    @Bean
    @Primary
    fun primaryEmbedding(
            @Qualifier("ollamaEmbeddingModel") delegate: EmbeddingModel,
    ): EmbeddingModel = delegate

    @Bean
    fun chatMemory(
        chatMemoryRepository: ChatMemoryRepository
    ): ChatMemory {
        // Ограничиваем окно памяти до 3 последних пар сообщений (user + assistant)
        // Это предотвращает переполнение контекста при длинных диалогах
        return MessageWindowChatMemory.builder()
            .chatMemoryRepository(chatMemoryRepository)
            .maxMessages(3)  // Хранить только последние 3 пары сообщений
            .build()
    }

    @Bean
    @Qualifier("ragChatClient")
    fun ragChatClient(
            @Qualifier("coderChatModel") coderChatModel: ChatModel,
            chatMemory: ChatMemory,
            loggingAdvisor: ChatClientLoggingAdvisor,
    ): ChatClient =
            ChatClient.builder(coderChatModel)
                    .defaultAdvisors(
                        loggingAdvisor,
                        MessageChatMemoryAdvisor.builder(chatMemory).build()
                    )
                    .build()

    /**
     * Быстрый ChatClient для простых запросов (извлечение класса/метода).
     * Использует более быструю модель (qwen2.5:0.5b) вместо большой модели.
     */
    @Bean
    @Qualifier("fastExtractionChatClient")
    fun fastExtractionChatClient(
            ollamaApi: OpenAiApi,
            loggingAdvisor: ChatClientLoggingAdvisor,
            retryTemplate: RetryTemplate,
    ): ChatClient {
        // Используем быструю модель для извлечения (qwen2.5:0.5b)
        // Эта модель намного быстрее, чем qwen2.5-coder:14b
        val fastModel = OpenAiChatModel.builder()
            .openAiApi(ollamaApi)
            .defaultOptions(
                OpenAiChatOptions.builder()
                    .model("qwen2.5:0.5b") // Быстрая модель вместо 14b
                    .temperature(0.0) // Детерминированность для извлечения
                    .topP(0.9)
                    .build()
            )
            .retryTemplate(retryTemplate)
            .build()
        
        return ChatClient.builder(fastModel)
                .defaultAdvisors(loggingAdvisor)
                .build()
    }

    /**
     * Клиент для структурированного извлечения данных (Entity/Relation Extraction).
     * Использует модель 1.5b, которая обеспечивает баланс между скоростью и
     * гарантией корректности JSON-структуры.
     */
    @Bean
    @Qualifier("structuredExtractionChatClient")
    fun structuredExtractionChatClient(
        ollamaApi: OpenAiApi,
        loggingAdvisor: ChatClientLoggingAdvisor,
        retryTemplate: RetryTemplate,
    ): ChatClient {
        val model = OpenAiChatModel.builder()
            .openAiApi(ollamaApi)
            .defaultOptions(
                OpenAiChatOptions.builder()
                    .model("qwen3.5:4b") // Оптимально для извлечения пар term-description
                    .temperature(0.0)      // Строгая детерминированность
                    .topP(0.1)            // Минимизируем разброс токенов для JSON
                    .build()
            )
            .retryTemplate(retryTemplate)
            .build()

        return ChatClient.builder(model)
            .defaultAdvisors(loggingAdvisor)
            .build()
    }

    /**
     * Fast-Check клиент для LLM-Судьи (Quality Gate этап 2).
     * Использует быструю модель Qwen 1.5B для проверки наличия бизнес-логики.
     */
    @Bean
    @Qualifier("fastCheckChatClient")
    fun fastCheckChatClient(
        ollamaApi: OpenAiApi,
        loggingAdvisor: ChatClientLoggingAdvisor,
        retryTemplate: RetryTemplate,
    ): ChatClient {
        val model = OpenAiChatModel.builder()
            .openAiApi(ollamaApi)
            .defaultOptions(
                OpenAiChatOptions.builder()
                    .model("qwen3.5:4b") // Qwen 1.5B для быстрой проверки
                    .temperature(0.0)      // Детерминированность для YES/NO ответов
                    .topP(0.1)
                    .build()
            )
            .retryTemplate(retryTemplate)
            .build()

        return ChatClient.builder(model)
            .defaultAdvisors(loggingAdvisor)
            .build()
    }
}
