package com.bftcom.docgenerator.graph.impl.node.builder.update

import com.bftcom.docgenerator.domain.application.Application
import com.bftcom.docgenerator.domain.enums.Lang
import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.domain.node.Node
import com.bftcom.docgenerator.graph.api.node.NodeUpdateData
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class NodeUpdateStrategyImplTest {
    private val strategy = NodeUpdateStrategyImpl()
    private val app = Application(id = 1L, key = "app", name = "App")

    @Test
    fun `hasChanges - codeHash одинаковый и sourceCode отличается - false (оптимизация)`() {
        val existing =
            node(
                fqn = "com.example.Foo",
                codeHash = "h1",
                sourceCode = "old",
                meta = mapOf("a" to 1),
            )
        val newData =
            updateData(
                codeHash = "h1",
                sourceCode = "new",
                meta = mapOf("a" to 1),
            )

        assertThat(strategy.hasChanges(existing, newData)).isFalse()
    }

    @Test
    fun `hasChanges - lineStart поменялся - true`() {
        val existing = node(fqn = "com.example.Foo", codeHash = "h1", lineStart = 1, lineEnd = 2)
        val newData = updateData(codeHash = "h1", lineStart = 10, lineEnd = 2)

        assertThat(strategy.hasChanges(existing, newData)).isTrue()
    }

    @Test
    fun `hasChanges - разные ключи meta - true`() {
        val existing = node(fqn = "com.example.Foo", meta = mapOf("a" to 1))
        val newData = updateData(meta = mapOf("a" to 1, "b" to 2))

        assertThat(strategy.hasChanges(existing, newData)).isTrue()
    }

    @Test
    fun `update - мерджит meta и удаляет null и пустые коллекции`() {
        val existing = node(fqn = "com.example.Foo", meta = mapOf("a" to 1, "keep" to listOf("x")))
        val newData =
            updateData(
                meta =
                    mapOf(
                        "a" to 1, // не меняем
                        "b" to 2, // добавляем
                        "emptyList" to emptyList<String>(), // должно выкинуться
                        "emptyMap" to emptyMap<String, Any>(), // должно выкинуться
                        "nullVal" to null, // должно выкинуться
                    ),
            )

        val updated = strategy.update(existing, newData)

        assertThat(updated.meta).containsEntry("a", 1)
        assertThat(updated.meta).containsEntry("b", 2)
        assertThat(updated.meta).containsEntry("keep", listOf("x"))
        assertThat(updated.meta).doesNotContainKeys("emptyList", "emptyMap", "nullVal")
    }

    private fun node(
        fqn: String,
        codeHash: String? = null,
        sourceCode: String? = null,
        lineStart: Int? = null,
        lineEnd: Int? = null,
        meta: Map<String, Any> = emptyMap(),
    ): Node =
        Node(
            id = 1L,
            application = app,
            fqn = fqn,
            name = fqn.substringAfterLast('.'),
            packageName = fqn.substringBeforeLast('.', missingDelimiterValue = ""),
            kind = NodeKind.CLASS,
            lang = Lang.kotlin,
            sourceCode = sourceCode,
            codeHash = codeHash,
            lineStart = lineStart,
            lineEnd = lineEnd,
            meta = meta,
        )

    private fun updateData(
        codeHash: String? = null,
        sourceCode: String? = null,
        lineStart: Int? = null,
        lineEnd: Int? = null,
        meta: Map<String, Any?> = emptyMap(),
    ): NodeUpdateData {
        @Suppress("UNCHECKED_CAST")
        val unsafeMeta = meta as Map<String, Any>

        return NodeUpdateData(
            name = "Foo",
            packageName = "com.example",
            kind = NodeKind.CLASS,
            lang = Lang.kotlin,
            parent = null,
            filePath = null,
            lineStart = lineStart,
            lineEnd = lineEnd,
            sourceCode = sourceCode,
            docComment = null,
            signature = null,
            codeHash = codeHash,
            meta = unsafeMeta,
        )
    }
}

