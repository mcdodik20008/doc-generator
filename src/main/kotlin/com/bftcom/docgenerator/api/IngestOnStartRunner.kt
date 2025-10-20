package com.bftcom.docgenerator.api

import org.slf4j.LoggerFactory
import org.springframework.boot.CommandLineRunner
import org.springframework.stereotype.Component

@Component
class IngestOnStartRunner(
    private val orchestrator: IngestOrchestrator
) : CommandLineRunner {
    private val log = LoggerFactory.getLogger(javaClass)
    override fun run(vararg args: String?) {
        if (args.contains("--ingest-on-start")) {
            val report = orchestrator.runOnce()
            log.info("IngestOnStart completed: {}", report)
        }
    }
}