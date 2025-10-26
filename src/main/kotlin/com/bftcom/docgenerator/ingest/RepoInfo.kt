package com.bftcom.docgenerator.ingest

data class RepoInfo(
    val provider: String?, // github|gitlab|bitbucket|gitea|other
    val owner: String?,
    val name: String?,
)
