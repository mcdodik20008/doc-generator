package com.bftcom.docgenerator.library.impl.integration

import com.bftcom.docgenerator.db.EdgeRepository
import com.bftcom.docgenerator.db.LibraryNodeRepository
import com.bftcom.docgenerator.db.NodeRepository
import com.bftcom.docgenerator.domain.application.Application
import com.bftcom.docgenerator.domain.enums.NodeKind
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.data.domain.PageRequest

class IntegrationPointLinkerImplTest {
    @Test
    fun `linkIntegrationPoints - пока ничего не создает`() {
        val nodeRepo = mockk<NodeRepository>()
        val libNodeRepo = mockk<LibraryNodeRepository>(relaxed = true)
        val edgeRepo = mockk<EdgeRepository>(relaxed = true)
        val svc = mockk<com.bftcom.docgenerator.library.api.integration.IntegrationPointService>(relaxed = true)

        every { nodeRepo.findAllByApplicationIdAndKindIn(1L, setOf(NodeKind.METHOD), any<PageRequest>()) } returns emptyList()

        val linker = IntegrationPointLinkerImpl(nodeRepo, libNodeRepo, edgeRepo, svc)
        val app = Application(id = 1L, key = "app", name = "App")

        val res = linker.linkIntegrationPoints(app)
        assertThat(res.httpEdgesCreated).isEqualTo(0)
        assertThat(res.kafkaEdgesCreated).isEqualTo(0)
        assertThat(res.camelEdgesCreated).isEqualTo(0)
        assertThat(res.errors).isEmpty()
    }
}

