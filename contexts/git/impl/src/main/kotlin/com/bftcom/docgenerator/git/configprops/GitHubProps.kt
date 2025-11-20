package com.bftcom.docgenerator.git.configprops

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "github")
data class GitHubProps(
    /** HTTPS URL репозитория */
    var url: String = "",
    /** Personal Access Token (если используется токен вместо user/pass) */
    var token: String = "",
    /** Имя пользователя (если не используется токен) */
    var username: String = "",
    /** Пароль (если не используется токен) */
    var password: String = "",
    /** Путь, куда будет клонироваться проект */
    var basePath: String = "/tmp/docgen/github",
)

