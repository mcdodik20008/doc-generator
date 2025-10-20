package com.bftcom.docgenerator.configprops

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(GitLabProps::class, DocgenIngestProps::class)
class PropsConfig
