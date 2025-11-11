package com.bftcom.docgenerator.git.api

import com.bftcom.docgenerator.git.model.IngestSummary

interface GitIngestOrchestrator {
    fun runOnce(
        appKey: String,
        repoPath: String,
        branch: String = "develop",
    ): IngestSummary
}
