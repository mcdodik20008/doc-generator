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

    @Test
    fun `update - обновляет все поля когда они изменились`() {
        val existing = node(
            fqn = "com.example.Foo",
            codeHash = "oldHash",
            sourceCode = "old code",
            lineStart = 1,
            lineEnd = 10,
        )
        val newData = updateData(
            codeHash = "newHash",
            sourceCode = "new code",
            lineStart = 5,
            lineEnd = 15,
        )

        val updated = strategy.update(existing, newData)

        assertThat(updated.codeHash).isEqualTo("newHash")
        assertThat(updated.sourceCode).isEqualTo("new code")
        assertThat(updated.lineStart).isEqualTo(5)
        assertThat(updated.lineEnd).isEqualTo(15)
    }

    @Test
    fun `update - не обновляет sourceCode когда codeHash не изменился`() {
        val existing = node(
            fqn = "com.example.Foo",
            codeHash = "sameHash",
            sourceCode = "original code",
        )
        val newData = updateData(
            codeHash = "sameHash",
            sourceCode = "different code",
        )

        val updated = strategy.update(existing, newData)

        assertThat(updated.sourceCode).isEqualTo("original code")
    }

    @Test
    fun `update - обновляет lineStart и lineEnd когда codeHash изменился`() {
        val existing = node(
            fqn = "com.example.Foo",
            codeHash = "oldHash",
            lineStart = 1,
            lineEnd = 10,
        )
        val newData = updateData(
            codeHash = "newHash",
            lineStart = 5,
            lineEnd = 15,
        )

        val updated = strategy.update(existing, newData)

        assertThat(updated.lineStart).isEqualTo(5)
        assertThat(updated.lineEnd).isEqualTo(15)
    }

    @Test
    fun `update - обновляет lineStart и lineEnd когда они явно указаны даже при том же codeHash`() {
        val existing = node(
            fqn = "com.example.Foo",
            codeHash = "sameHash",
            lineStart = 1,
            lineEnd = 10,
        )
        val newData = updateData(
            codeHash = "sameHash",
            lineStart = 5,
            lineEnd = 15,
        )

        val updated = strategy.update(existing, newData)

        assertThat(updated.lineStart).isEqualTo(5)
        assertThat(updated.lineEnd).isEqualTo(15)
    }

    @Test
    fun `update - не обновляет lineStart и lineEnd когда codeHash не изменился и они не указаны`() {
        val existing = node(
            fqn = "com.example.Foo",
            codeHash = "sameHash",
            lineStart = 1,
            lineEnd = 10,
        )
        val newData = updateData(
            codeHash = "sameHash",
            lineStart = null,
            lineEnd = null,
        )

        val updated = strategy.update(existing, newData)

        assertThat(updated.lineStart).isEqualTo(1)
        assertThat(updated.lineEnd).isEqualTo(10)
    }

    @Test
    fun `update - сохраняет непустые коллекции в meta`() {
        val existing = node(fqn = "com.example.Foo", meta = emptyMap())
        val newData = updateData(
            meta = mapOf(
                "list" to listOf("a", "b"),
                "map" to mapOf("key" to "value"),
            ),
        )

        val updated = strategy.update(existing, newData)

        assertThat(updated.meta).containsEntry("list", listOf("a", "b"))
        assertThat(updated.meta).containsEntry("map", mapOf("key" to "value"))
    }

    @Test
    fun `hasChanges - codeHash изменился - true`() {
        val existing = node(fqn = "com.example.Foo", codeHash = "oldHash")
        val newData = updateData(codeHash = "newHash")

        assertThat(strategy.hasChanges(existing, newData)).isTrue()
    }

    @Test
    fun `hasChanges - name изменился - true`() {
        val existing = node(fqn = "com.example.Foo", name = "OldName")
        val newData = updateData(name = "NewName")

        assertThat(strategy.hasChanges(existing, newData)).isTrue()
    }

    @Test
    fun `hasChanges - packageName изменился - true`() {
        val existing = node(fqn = "com.example.Foo", packageName = "com.old")
        val newData = updateData(packageName = "com.new")

        assertThat(strategy.hasChanges(existing, newData)).isTrue()
    }

    @Test
    fun `hasChanges - kind изменился - true`() {
        val existing = node(fqn = "com.example.Foo", kind = NodeKind.CLASS)
        val newData = updateData(kind = NodeKind.INTERFACE)

        assertThat(strategy.hasChanges(existing, newData)).isTrue()
    }

    @Test
    fun `hasChanges - все поля одинаковые - false`() {
        val existing = node(
            fqn = "com.example.Foo",
            codeHash = "hash",
            name = "Foo",
            packageName = "com.example",
            kind = NodeKind.CLASS,
        )
        val newData = updateData(
            codeHash = "hash",
            name = "Foo",
            packageName = "com.example",
            kind = NodeKind.CLASS,
        )

        assertThat(strategy.hasChanges(existing, newData)).isFalse()
    }

    private fun node(
        fqn: String,
        codeHash: String? = null,
        sourceCode: String? = null,
        lineStart: Int? = null,
        lineEnd: Int? = null,
        meta: Map<String, Any> = emptyMap(),
        name: String? = null,
        packageName: String? = null,
        kind: NodeKind = NodeKind.CLASS,
    ): Node =
        Node(
            id = 1L,
            application = app,
            fqn = fqn,
            name = name ?: fqn.substringAfterLast('.'),
            packageName = packageName ?: fqn.substringBeforeLast('.', missingDelimiterValue = ""),
            kind = kind,
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
        name: String = "Foo",
        packageName: String = "com.example",
        kind: NodeKind = NodeKind.CLASS,
    ): NodeUpdateData {
        @Suppress("UNCHECKED_CAST")
        val unsafeMeta = meta as Map<String, Any>

        return NodeUpdateData(
            name = name,
            packageName = packageName,
            kind = kind,
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

