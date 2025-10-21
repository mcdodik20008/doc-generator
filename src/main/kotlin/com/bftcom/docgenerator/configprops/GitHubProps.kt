package com.bftcom.docgenerator.configprops

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties(prefix = "github")
data class GitHubProps(
    /** HTTPS URL репозитория */
    var repoUrl: String = "",

    /** Ветка по умолчанию */
    var branch: String = "main",

    /** Personal Access Token (если используется токен вместо user/pass) */
    var token: String = "",

    /** Имя пользователя (если не используется токен) */
    var username: String = "",

    /** Пароль (если не используется токен) */
    var password: String = "",

    /** Путь, куда будет клонироваться проект */
    var basePath: String = "/tmp/docgen/github"
)
