package com.bftcom.docgenerator.ai.config

import com.bftcom.docgenerator.ai.props.AiClientsProperties
import org.springframework.ai.chat.client.ChatClient
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
            OpenAiChatOptions
                .builder()
                .model(p.model)
                .temperature(p.temperature)
                .topP(p.topP)
                .seed(p.seed)
                .build()
        return OpenAiChatModel
            .builder()
            .openAiApi(ollamaApi)
            .defaultOptions(options)
            .build()
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
            OpenAiChatOptions
                .builder()
                .model(p.model)
                .temperature(p.temperature)
                .topP(p.topP)
                .seed(p.seed)
                .build()
        return OpenAiChatModel
            .builder()
            .openAiApi(ollamaApi)
            .defaultOptions(options)
            .build()
    }

    /** Удобные ChatClient’ы с дефолтным system для каждой роли */
    @Bean
    @Primary
    @Qualifier("coderChatClient")
    fun coderChatClient(
        @Qualifier("coderChatModel")
        coderChatModel: ChatModel,
        props: AiClientsProperties,
    ): ChatClient =
        ChatClient
            .builder(coderChatModel)
            .defaultSystem(props.coder.system.trim())
            .build()

    @Bean
    @Qualifier("talkerChatClient")
    fun talkerChatClient(
        @Qualifier("talkerChatModel")
        talkerChatModel: ChatModel,
        props: AiClientsProperties,
    ): ChatClient =
        ChatClient
            .builder(talkerChatModel)
            .defaultSystem(props.talker.system.trim())
            .build()

    @Bean
    @Primary
    fun primaryEmbedding(
        @Qualifier("ollamaEmbeddingModel") delegate: EmbeddingModel,
    ): EmbeddingModel = delegate
}
