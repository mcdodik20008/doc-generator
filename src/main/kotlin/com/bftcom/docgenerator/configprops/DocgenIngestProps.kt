package com.bftcom.docgenerator.configprops

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "docgen")
data class DocgenIngestProps(
    var appKey: String = "doc-generator",
    var checkoutDir: String = "/tmp/docgen/checkout"
)