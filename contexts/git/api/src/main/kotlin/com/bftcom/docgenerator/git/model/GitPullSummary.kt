package com.bftcom.docgenerator.git.model

import java.nio.file.Path
import java.time.OffsetDateTime

data class GitPullSummary(
    val repoUrl: String,
    val branch: String,
    val appKey: String,
    val localPath: Path,

    val operation: GitOperation,
    val beforeHead: String?,
    val afterHead: String?,

    val fetchedAt: OffsetDateTime,
) {
    val updated: Boolean get() = beforeHead != afterHead
}
