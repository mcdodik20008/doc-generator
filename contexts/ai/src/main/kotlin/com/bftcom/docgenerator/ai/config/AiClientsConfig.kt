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

@Configuration
class AiClientsConfig {
    /** ChatModel для coder с собственными дефолтными опциями */
    @Qualifier("coderChatModel")
    @Bean(name = ["coderChatModel"])
    fun coderChatModel(
            ollamaApi: OpenAiApi,
            props: AiClientsProperties,
    ): OpenAiChatModel {
        val p = props.coder
        val options =
                OpenAiChatOptions.builder()
                        .model(p.model)
                        .temperature(p.temperature)
                        .topP(p.topP)
                        .seed(p.seed)
                        .build()
        return OpenAiChatModel.builder().openAiApi(ollamaApi).defaultOptions(options).build()
    }

    /** ChatModel для talker с собственными опциями */
    @Qualifier("talkerChatModel")
    @Bean(name = ["talkerChatModel"])
    fun talkerChatModel(
            ollamaApi: OpenAiApi,
            props: AiClientsProperties,
    ): OpenAiChatModel {
        val p = props.talker
        val options =
                OpenAiChatOptions.builder()
                        .model(p.model)
                        .temperature(p.temperature)
                        .topP(p.topP)
                        .seed(p.seed)
                        .build()
        return OpenAiChatModel.builder().openAiApi(ollamaApi).defaultOptions(options).build()
    }

    /** Удобные ChatClient'ы с дефолтным system для каждой роли */
    @Bean
    @Primary
    @Qualifier("coderChatClient")
    fun coderChatClient(
            @Qualifier("coderChatModel") coderChatModel: ChatModel,
            props: AiClientsProperties,
            loggingAdvisor: ChatClientLoggingAdvisor,
    ): ChatClient =
            ChatClient.builder(coderChatModel)
                    .defaultSystem(props.coder.system.trim())
                    .defaultAdvisors(loggingAdvisor)
                    .build()

    @Bean
    @Qualifier("talkerChatClient")
    fun talkerChatClient(
            @Qualifier("talkerChatModel") talkerChatModel: ChatModel,
            props: AiClientsProperties,
            loggingAdvisor: ChatClientLoggingAdvisor,
    ): ChatClient =
            ChatClient.builder(talkerChatModel)
                    .defaultSystem(props.talker.system.trim())
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
        return MessageWindowChatMemory.builder().chatMemoryRepository(chatMemoryRepository).build()
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
            .build()
        
        return ChatClient.builder(fastModel)
                .defaultAdvisors(loggingAdvisor)
                .build()
    }
}
