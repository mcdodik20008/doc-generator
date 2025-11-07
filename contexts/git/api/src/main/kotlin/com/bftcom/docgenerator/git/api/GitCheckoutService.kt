package com.bftcom.docgenerator.git.com.bftcom.docgenerator.git.api

import java.nio.file.Path

interface GitCheckoutService {
    fun checkoutOrUpdate(
        repoUrl: String,
        branch: String,
        token: String,
        username: String,
        password: String,
        checkoutDir: Path,
    ): Path

    fun resolveRepoUrl(
        baseUrlOrFull: String,
        repoPath: String,
    )
}