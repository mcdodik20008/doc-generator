package com.bftcom.docgenerator.configprops

import com.bftcom.docgenerator.config.DocEvaluatorProperties
import com.bftcom.docgenerator.git.configprops.GitHubProps
import com.bftcom.docgenerator.git.configprops.GitLabProps
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(
        GitLabProps::class,
        GitHubProps::class,
        DocEvaluatorProperties::class
)
class PropsConfig
