package com.bftcom.docgenerator

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class DocGeneratorApplication

fun main(args: Array<String>) {
    runApplication<DocGeneratorApplication>(*args)
}
