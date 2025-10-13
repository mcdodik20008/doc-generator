package com.bftcom.docgenerator.core.api

import com.bftcom.docgenerator.core.model.DepLine
import com.bftcom.docgenerator.core.model.UsageLine

interface ContextBuilder {
    fun depsContext(
        depIds: List<Long>,
        locale: String = "ru",
    ): List<DepLine>

    fun usageContext(
        userIds: List<Long>,
        locale: String = "ru",
    ): List<UsageLine>
}
