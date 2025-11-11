package com.bftcom.docgenerator.git.configprops

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Primary

@Primary
@ConfigurationProperties(prefix = "gitlab")
data class GitLabProps(
    /** HTTPS URL репозитория */
    var url: String = "",
    /** Personal Access Token (если используется токен вместо user/pass) */
    var token: String = "",
    /** Имя пользователя (если не используется токен) */
    var username: String = "",
    /** Пароль (если не используется токен) */
    var password: String = "",
    /** Путь, куда будет клонироваться проект */
    var basePath: String = "/tmp/docgen/gitlab",
)
