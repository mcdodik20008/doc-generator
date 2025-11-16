package com.bftcom.docgenerator

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.annotation.EnableScheduling

@EnableScheduling
@EnableAsync
@SpringBootApplication
@ConfigurationPropertiesScan
class DocGeneratorApplication

fun main(args: Array<String>) {
    runApplication<DocGeneratorApplication>(*args)
}
