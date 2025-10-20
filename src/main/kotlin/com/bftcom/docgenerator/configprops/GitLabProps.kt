package com.bftcom.docgenerator.configprops

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "gitlab")
data class GitLabProps(
    var repoUrl: String = "",
    var branch: String = "main",
    var username: String = "",
    var password: String = "",
)
