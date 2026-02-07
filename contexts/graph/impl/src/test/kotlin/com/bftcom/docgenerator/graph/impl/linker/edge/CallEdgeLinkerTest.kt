package com.bftcom.docgenerator.graph.impl.linker.edge

import com.bftcom.docgenerator.domain.application.Application
import com.bftcom.docgenerator.domain.enums.EdgeKind
import com.bftcom.docgenerator.domain.enums.Lang
import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.domain.node.Node
import com.bftcom.docgenerator.shared.node.NodeMeta
import com.bftcom.docgenerator.shared.node.RawUsage
import com.bftcom.docgenerator.graph.impl.linker.NodeIndexFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CallEdgeLinkerTest {
    private val app = Application(id = 1L, key = "app", name = "App")

    @Test
    fun `link - Simple usage with owner resolves via findMethodsByName fallback`() {
        // given
        val owner = node(fqn = "com.example.Owner", name = "Owner", pkg = "com.example", kind = NodeKind.CLASS)
        val method = node(fqn = "com.example.Owner.method()", name = "method", pkg = "com.example", kind = NodeKind.METHOD)
        val caller = node(fqn = "com.example.Caller()", name = "Caller", pkg = "com.example", kind = NodeKind.METHOD)

        val index = NodeIndexFactory().create(listOf(owner, method, caller))
        val meta = NodeMeta(ownerFqn = "com.example.Owner", rawUsages = listOf(RawUsage.Simple("method", isCall = true)))

        // when
        val edges = CallEdgeLinker().link(caller, meta, index)

        // then — findByFqn("com.example.Owner.method") misses, fallback finds "com.example.Owner.method()"
        assertThat(edges).containsExactly(
            Triple(caller, method, EdgeKind.CALLS_CODE),
        )
    }

    @Test
    fun `link - Simple usage without owner resolves via type resolution`() {
        // given
        val target = node(fqn = "com.example.Target", name = "Target", pkg = "com.example", kind = NodeKind.CLASS)
        val caller = node(fqn = "com.example.Caller", name = "Caller", pkg = "com.example", kind = NodeKind.METHOD)

        val index = NodeIndexFactory().create(listOf(target, caller))
        val meta = NodeMeta(
            imports = listOf("com.example.Target"),
            rawUsages = listOf(RawUsage.Simple("Target", isCall = true)),
        )

        // when
        val edges = CallEdgeLinker().link(caller, meta, index)

        // then
        assertThat(edges).containsExactly(
            Triple(caller, target, EdgeKind.CALLS_CODE),
        )
    }

    @Test
    fun `link - Simple usage with isCall false does not create edge`() {
        // given
        val target = node(fqn = "com.example.Target", name = "Target", pkg = "com.example", kind = NodeKind.CLASS)
        val caller = node(fqn = "com.example.Caller", name = "Caller", pkg = "com.example", kind = NodeKind.METHOD)

        val index = NodeIndexFactory().create(listOf(target, caller))
        val meta = NodeMeta(
            imports = listOf("com.example.Target"),
            rawUsages = listOf(RawUsage.Simple("Target", isCall = false)),
        )

        // when
        val edges = CallEdgeLinker().link(caller, meta, index)

        // then
        assertThat(edges).isEmpty()
    }

    @Test
    fun `link - Simple usage with owner but no matching FQN falls back to type resolution`() {
        // given
        val owner = node(fqn = "com.example.Owner", name = "Owner", pkg = "com.example", kind = NodeKind.CLASS)
        val target = node(fqn = "com.example.Target", name = "Target", pkg = "com.example", kind = NodeKind.CLASS)
        val caller = node(fqn = "com.example.Caller", name = "Caller", pkg = "com.example", kind = NodeKind.METHOD)

        val index = NodeIndexFactory().create(listOf(owner, target, caller))
        val meta = NodeMeta(
            ownerFqn = "com.example.Owner",
            imports = listOf("com.example.Target"),
            rawUsages = listOf(RawUsage.Simple("Target", isCall = true)),
        )

        // when
        val edges = CallEdgeLinker().link(caller, meta, index)

        // then - should fallback to type resolution
        assertThat(edges).containsExactly(
            Triple(caller, target, EdgeKind.CALLS_CODE),
        )
    }

    @Test
    fun `link - Dot usage with uppercase receiver resolves via type resolution`() {
        // given
        val receiverType = node(fqn = "com.example.Receiver", name = "Receiver", pkg = "com.example", kind = NodeKind.CLASS)
        val method = node(fqn = "com.example.Receiver.method()", name = "method", pkg = "com.example", kind = NodeKind.METHOD)
        val caller = node(fqn = "com.example.Caller()", name = "Caller", pkg = "com.example", kind = NodeKind.METHOD)

        val index = NodeIndexFactory().create(listOf(receiverType, method, caller))
        val meta = NodeMeta(
            imports = listOf("com.example.Receiver"),
            rawUsages = listOf(RawUsage.Dot(receiver = "Receiver", member = "method", isCall = true)),
        )

        // when
        val edges = CallEdgeLinker().link(caller, meta, index)

        // then
        assertThat(edges).containsExactly(
            Triple(caller, method, EdgeKind.CALLS_CODE),
        )
    }

    @Test
    fun `link - Dot usage with lowercase receiver resolves via owner`() {
        // given
        val owner = node(fqn = "com.example.Owner", name = "Owner", pkg = "com.example", kind = NodeKind.CLASS)
        val method = node(fqn = "com.example.Owner.method()", name = "method", pkg = "com.example", kind = NodeKind.METHOD)
        val caller = node(fqn = "com.example.Caller()", name = "Caller", pkg = "com.example", kind = NodeKind.METHOD)

        val index = NodeIndexFactory().create(listOf(owner, method, caller))
        val meta = NodeMeta(
            ownerFqn = "com.example.Owner",
            rawUsages = listOf(RawUsage.Dot(receiver = "owner", member = "method", isCall = true)),
        )

        // when
        val edges = CallEdgeLinker().link(caller, meta, index)

        // then
        assertThat(edges).containsExactly(
            Triple(caller, method, EdgeKind.CALLS_CODE),
        )
    }

    @Test
    fun `link - Dot usage with no receiver type does not create edge`() {
        // given
        val caller = node(fqn = "com.example.Caller", name = "Caller", pkg = "com.example", kind = NodeKind.METHOD)

        val index = NodeIndexFactory().create(listOf(caller))
        val meta = NodeMeta(
            rawUsages = listOf(RawUsage.Dot(receiver = "unknown", member = "method", isCall = true)),
        )

        // when
        val edges = CallEdgeLinker().link(caller, meta, index)

        // then
        assertThat(edges).isEmpty()
    }

    @Test
    fun `link - Dot usage with uppercase receiver but no matching type does not create edge`() {
        // given
        val caller = node(fqn = "com.example.Caller", name = "Caller", pkg = "com.example", kind = NodeKind.METHOD)

        val index = NodeIndexFactory().create(listOf(caller))
        val meta = NodeMeta(
            imports = listOf("com.example.Other"),
            rawUsages = listOf(RawUsage.Dot(receiver = "UnknownType", member = "method", isCall = true)),
        )

        // when
        val edges = CallEdgeLinker().link(caller, meta, index)

        // then
        assertThat(edges).isEmpty()
    }

    @Test
    fun `link - Dot usage with owner but no matching method does not create edge`() {
        // given
        val owner = node(fqn = "com.example.Owner", name = "Owner", pkg = "com.example", kind = NodeKind.CLASS)
        val caller = node(fqn = "com.example.Caller", name = "Caller", pkg = "com.example", kind = NodeKind.METHOD)

        val index = NodeIndexFactory().create(listOf(owner, caller))
        val meta = NodeMeta(
            ownerFqn = "com.example.Owner",
            rawUsages = listOf(RawUsage.Dot(receiver = "owner", member = "nonExistentMethod", isCall = true)),
        )

        // when
        val edges = CallEdgeLinker().link(caller, meta, index)

        // then
        assertThat(edges).isEmpty()
    }

    @Test
    fun `link - empty rawUsages returns empty list`() {
        // given
        val caller = node(fqn = "com.example.Caller", name = "Caller", pkg = "com.example", kind = NodeKind.METHOD)

        val index = NodeIndexFactory().create(listOf(caller))
        val meta = NodeMeta(rawUsages = emptyList())

        // when
        val edges = CallEdgeLinker().link(caller, meta, index)

        // then
        assertThat(edges).isEmpty()
    }

    @Test
    fun `link - null rawUsages returns empty list`() {
        // given
        val caller = node(fqn = "com.example.Caller", name = "Caller", pkg = "com.example", kind = NodeKind.METHOD)

        val index = NodeIndexFactory().create(listOf(caller))
        val meta = NodeMeta(rawUsages = null)

        // when
        val edges = CallEdgeLinker().link(caller, meta, index)

        // then
        assertThat(edges).isEmpty()
    }

    @Test
    fun `link - mixed Simple and Dot usages creates multiple edges`() {
        // given
        val owner = node(fqn = "com.example.Owner", name = "Owner", pkg = "com.example", kind = NodeKind.CLASS)
        val method1 = node(fqn = "com.example.Owner.method1()", name = "method1", pkg = "com.example", kind = NodeKind.METHOD)
        val method2 = node(fqn = "com.example.Owner.method2()", name = "method2", pkg = "com.example", kind = NodeKind.METHOD)
        val target = node(fqn = "com.example.Target", name = "Target", pkg = "com.example", kind = NodeKind.CLASS)
        val caller = node(fqn = "com.example.Caller", name = "Caller", pkg = "com.example", kind = NodeKind.METHOD)

        val index = NodeIndexFactory().create(listOf(owner, method1, method2, target, caller))
        val meta = NodeMeta(
            ownerFqn = "com.example.Owner",
            imports = listOf("com.example.Target"),
            rawUsages = listOf(
                RawUsage.Simple("method1", isCall = true),
                RawUsage.Dot(receiver = "owner", member = "method2", isCall = true),
                RawUsage.Simple("Target", isCall = true),
            ),
        )

        // when
        val edges = CallEdgeLinker().link(caller, meta, index)

        // then
        assertThat(edges).containsExactlyInAnyOrder(
            Triple(caller, method1, EdgeKind.CALLS_CODE),
            Triple(caller, method2, EdgeKind.CALLS_CODE),
            Triple(caller, target, EdgeKind.CALLS_CODE),
        )
    }

    @Test
    fun `link - Dot usage with empty receiver first char uses owner`() {
        // given
        val owner = node(fqn = "com.example.Owner", name = "Owner", pkg = "com.example", kind = NodeKind.CLASS)
        val method = node(fqn = "com.example.Owner.method()", name = "method", pkg = "com.example", kind = NodeKind.METHOD)
        val caller = node(fqn = "com.example.Caller()", name = "Caller", pkg = "com.example", kind = NodeKind.METHOD)

        val index = NodeIndexFactory().create(listOf(owner, method, caller))
        val meta = NodeMeta(
            ownerFqn = "com.example.Owner",
            rawUsages = listOf(RawUsage.Dot(receiver = "", member = "method", isCall = true)),
        )

        // when
        val edges = CallEdgeLinker().link(caller, meta, index)

        // then - empty receiver first char is null, should use owner
        assertThat(edges).containsExactly(
            Triple(caller, method, EdgeKind.CALLS_CODE),
        )
    }

    @Test
    fun `link - циклический вызов A вызывает B B вызывает A`() {
        // given
        val nodeA = node(fqn = "com.example.A", name = "A", pkg = "com.example", kind = NodeKind.METHOD)
        val nodeB = node(fqn = "com.example.B", name = "B", pkg = "com.example", kind = NodeKind.METHOD)

        val index = NodeIndexFactory().create(listOf(nodeA, nodeB))

        // A вызывает B
        val metaA = NodeMeta(
            ownerFqn = "com.example.Owner",
            imports = listOf("com.example.B"),
            rawUsages = listOf(RawUsage.Simple("B", isCall = true)),
        )

        // B вызывает A
        val metaB = NodeMeta(
            ownerFqn = "com.example.Owner",
            imports = listOf("com.example.A"),
            rawUsages = listOf(RawUsage.Simple("A", isCall = true)),
        )

        // when
        val edgesA = CallEdgeLinker().link(nodeA, metaA, index)
        val edgesB = CallEdgeLinker().link(nodeB, metaB, index)

        // then
        assertThat(edgesA).contains(Triple(nodeA, nodeB, EdgeKind.CALLS_CODE))
        assertThat(edgesB).contains(Triple(nodeB, nodeA, EdgeKind.CALLS_CODE))
    }

    @Test
    fun `link - цепочка вызовов с циклом A вызывает B B вызывает C C вызывает A`() {
        // given
        val nodeA = node(fqn = "com.example.A", name = "A", pkg = "com.example", kind = NodeKind.METHOD)
        val nodeB = node(fqn = "com.example.B", name = "B", pkg = "com.example", kind = NodeKind.METHOD)
        val nodeC = node(fqn = "com.example.C", name = "C", pkg = "com.example", kind = NodeKind.METHOD)

        val index = NodeIndexFactory().create(listOf(nodeA, nodeB, nodeC))

        // A вызывает B
        val metaA = NodeMeta(
            ownerFqn = "com.example.Owner",
            imports = listOf("com.example.B"),
            rawUsages = listOf(RawUsage.Simple("B", isCall = true)),
        )

        // B вызывает C
        val metaB = NodeMeta(
            ownerFqn = "com.example.Owner",
            imports = listOf("com.example.C"),
            rawUsages = listOf(RawUsage.Simple("C", isCall = true)),
        )

        // C вызывает A
        val metaC = NodeMeta(
            ownerFqn = "com.example.Owner",
            imports = listOf("com.example.A"),
            rawUsages = listOf(RawUsage.Simple("A", isCall = true)),
        )

        // when
        val edgesA = CallEdgeLinker().link(nodeA, metaA, index)
        val edgesB = CallEdgeLinker().link(nodeB, metaB, index)
        val edgesC = CallEdgeLinker().link(nodeC, metaC, index)

        // then
        assertThat(edgesA).contains(Triple(nodeA, nodeB, EdgeKind.CALLS_CODE))
        assertThat(edgesB).contains(Triple(nodeB, nodeC, EdgeKind.CALLS_CODE))
        assertThat(edgesC).contains(Triple(nodeC, nodeA, EdgeKind.CALLS_CODE))
    }

    @Test
    fun `link - множественные циклические зависимости через Dot usage`() {
        // given
        val receiver = node(fqn = "com.example.Receiver", name = "Receiver", pkg = "com.example", kind = NodeKind.CLASS)
        val methodA = node(fqn = "com.example.Receiver.methodA()", name = "methodA", pkg = "com.example", kind = NodeKind.METHOD)
        val methodB = node(fqn = "com.example.Receiver.methodB()", name = "methodB", pkg = "com.example", kind = NodeKind.METHOD)

        val index = NodeIndexFactory().create(listOf(receiver, methodA, methodB))

        // methodA вызывает methodB через receiver.methodB
        val metaA = NodeMeta(
            ownerFqn = "com.example.Receiver",
            rawUsages = listOf(RawUsage.Dot(receiver = "receiver", member = "methodB", isCall = true)),
        )

        // methodB вызывает methodA через receiver.methodA
        val metaB = NodeMeta(
            ownerFqn = "com.example.Receiver",
            rawUsages = listOf(RawUsage.Dot(receiver = "receiver", member = "methodA", isCall = true)),
        )

        // when
        val edgesA = CallEdgeLinker().link(methodA, metaA, index)
        val edgesB = CallEdgeLinker().link(methodB, metaB, index)

        // then
        assertThat(edgesA).contains(Triple(methodA, methodB, EdgeKind.CALLS_CODE))
        assertThat(edgesB).contains(Triple(methodB, methodA, EdgeKind.CALLS_CODE))
    }

    @Test
    fun `link - самоссылка метод вызывает сам себя`() {
        // given
        val method = node(fqn = "com.example.Method.selfCall()", name = "selfCall", pkg = "com.example", kind = NodeKind.METHOD)

        val index = NodeIndexFactory().create(listOf(method))

        // Метод вызывает сам себя
        val meta = NodeMeta(
            ownerFqn = "com.example.Method",
            rawUsages = listOf(RawUsage.Simple("selfCall", isCall = true)),
        )

        // when
        val edges = CallEdgeLinker().link(method, meta, index)

        // then
        assertThat(edges).contains(Triple(method, method, EdgeKind.CALLS_CODE))
    }

    @Test
    fun `link - null imports использует пустой список`() {
        val owner = node(fqn = "com.example.Owner", name = "Owner", pkg = "com.example", kind = NodeKind.CLASS)
        val method = node(fqn = "com.example.Owner.method()", name = "method", pkg = "com.example", kind = NodeKind.METHOD)
        val caller = node(fqn = "com.example.Caller()", name = "Caller", pkg = "com.example", kind = NodeKind.METHOD)

        val index = NodeIndexFactory().create(listOf(owner, method, caller))
        val meta = NodeMeta(ownerFqn = "com.example.Owner", rawUsages = listOf(RawUsage.Simple("method", isCall = true)), imports = null)

        val edges = CallEdgeLinker().link(caller, meta, index)

        assertThat(edges).containsExactly(Triple(caller, method, EdgeKind.CALLS_CODE))
    }

    @Test
    fun `link - null packageName использует пустую строку`() {
        val owner = node(fqn = "com.example.Owner", name = "Owner", pkg = "com.example", kind = NodeKind.CLASS)
        val method = node(fqn = "com.example.Owner.method()", name = "method", pkg = "com.example", kind = NodeKind.METHOD)
        val caller = node(fqn = "com.example.Caller()", name = "Caller", pkg = null, kind = NodeKind.METHOD)

        val index = NodeIndexFactory().create(listOf(owner, method, caller))
        val meta = NodeMeta(ownerFqn = "com.example.Owner", rawUsages = listOf(RawUsage.Simple("method", isCall = true)))

        val edges = CallEdgeLinker().link(caller, meta, index)

        assertThat(edges).containsExactly(Triple(caller, method, EdgeKind.CALLS_CODE))
    }

    @Test
    fun `link - Dot usage с null ownerFqn не использует owner`() {
        val receiverType = node(fqn = "com.example.Receiver", name = "Receiver", pkg = "com.example", kind = NodeKind.CLASS)
        val method = node(fqn = "com.example.Receiver.method()", name = "method", pkg = "com.example", kind = NodeKind.METHOD)
        val caller = node(fqn = "com.example.Caller()", name = "Caller", pkg = "com.example", kind = NodeKind.METHOD)

        val index = NodeIndexFactory().create(listOf(receiverType, method, caller))
        val meta = NodeMeta(
            ownerFqn = null,
            rawUsages = listOf(RawUsage.Dot(receiver = "receiver", member = "method", isCall = true)),
            imports = emptyList(),
        )

        val edges = CallEdgeLinker().link(caller, meta, index)

        assertThat(edges).isEmpty()
    }

    @Test
    fun `link - Dot usage с lowercase receiver без owner не создает edge`() {
        val caller = node(fqn = "com.example.Caller", name = "Caller", pkg = "com.example", kind = NodeKind.METHOD)

        val index = NodeIndexFactory().create(listOf(caller))
        val meta = NodeMeta(
            ownerFqn = null,
            rawUsages = listOf(RawUsage.Dot(receiver = "receiver", member = "method", isCall = true)),
            imports = emptyList(),
        )

        val edges = CallEdgeLinker().link(caller, meta, index)

        assertThat(edges).isEmpty()
    }

    @Test
    fun `link - множественные Simple usages с разными исходами создают все edges`() {
        val owner = node(fqn = "com.example.Owner", name = "Owner", pkg = "com.example", kind = NodeKind.CLASS)
        val method1 = node(fqn = "com.example.Owner.method1()", name = "method1", pkg = "com.example", kind = NodeKind.METHOD)
        val target = node(fqn = "com.example.Target", name = "Target", pkg = "com.example", kind = NodeKind.CLASS)
        val caller = node(fqn = "com.example.Caller", name = "Caller", pkg = "com.example", kind = NodeKind.METHOD)

        val index = NodeIndexFactory().create(listOf(owner, method1, target, caller))
        val meta = NodeMeta(
            ownerFqn = "com.example.Owner",
            imports = listOf("com.example.Target"),
            rawUsages = listOf(
                RawUsage.Simple("method1", isCall = true),
                RawUsage.Simple("Target", isCall = true),
            ),
        )

        val edges = CallEdgeLinker().link(caller, meta, index)

        assertThat(edges).containsExactlyInAnyOrder(
            Triple(caller, method1, EdgeKind.CALLS_CODE),
            Triple(caller, target, EdgeKind.CALLS_CODE),
        )
    }

    @Test
    fun `link - множественные Dot usages создают все edges`() {
        val receiverType1 = node(fqn = "com.example.Receiver1", name = "Receiver1", pkg = "com.example", kind = NodeKind.CLASS)
        val method1 = node(fqn = "com.example.Receiver1.method1()", name = "method1", pkg = "com.example", kind = NodeKind.METHOD)
        val owner = node(fqn = "com.example.Owner", name = "Owner", pkg = "com.example", kind = NodeKind.CLASS)
        val method2 = node(fqn = "com.example.Owner.method2()", name = "method2", pkg = "com.example", kind = NodeKind.METHOD)
        val caller = node(fqn = "com.example.Caller", name = "Caller", pkg = "com.example", kind = NodeKind.METHOD)

        val index = NodeIndexFactory().create(listOf(receiverType1, method1, owner, method2, caller))
        val meta = NodeMeta(
            ownerFqn = "com.example.Owner",
            imports = listOf("com.example.Receiver1"),
            rawUsages = listOf(
                RawUsage.Dot(receiver = "Receiver1", member = "method1", isCall = true),
                RawUsage.Dot(receiver = "owner", member = "method2", isCall = true),
            ),
        )

        val edges = CallEdgeLinker().link(caller, meta, index)

        assertThat(edges).containsExactlyInAnyOrder(
            Triple(caller, method1, EdgeKind.CALLS_CODE),
            Triple(caller, method2, EdgeKind.CALLS_CODE),
        )
    }

    @Test
    fun `link - цепочка вызовов с самоссылкой в середине A вызывает B B вызывает B B вызывает C`() {
        val nodeA = node(fqn = "com.example.A", name = "A", pkg = "com.example", kind = NodeKind.METHOD)
        val nodeB = node(fqn = "com.example.B", name = "B", pkg = "com.example", kind = NodeKind.METHOD)
        val nodeC = node(fqn = "com.example.C", name = "C", pkg = "com.example", kind = NodeKind.METHOD)

        val index = NodeIndexFactory().create(listOf(nodeA, nodeB, nodeC))

        val metaA = NodeMeta(
            ownerFqn = "com.example.Owner",
            imports = listOf("com.example.B"),
            rawUsages = listOf(RawUsage.Simple("B", isCall = true)),
        )

        val metaB = NodeMeta(
            ownerFqn = "com.example.Owner",
            imports = listOf("com.example.B", "com.example.C"),
            rawUsages = listOf(
                RawUsage.Simple("B", isCall = true),
                RawUsage.Simple("C", isCall = true),
            ),
        )

        val edgesA = CallEdgeLinker().link(nodeA, metaA, index)
        val edgesB = CallEdgeLinker().link(nodeB, metaB, index)

        assertThat(edgesA).contains(Triple(nodeA, nodeB, EdgeKind.CALLS_CODE))
        assertThat(edgesB).contains(Triple(nodeB, nodeB, EdgeKind.CALLS_CODE))
        assertThat(edgesB).contains(Triple(nodeB, nodeC, EdgeKind.CALLS_CODE))
    }

    @Test
    fun `link - множество циклических зависимостей в одной цепочке`() {
        val nodeA = node(fqn = "com.example.A", name = "A", pkg = "com.example", kind = NodeKind.METHOD)
        val nodeB = node(fqn = "com.example.B", name = "B", pkg = "com.example", kind = NodeKind.METHOD)
        val nodeC = node(fqn = "com.example.C", name = "C", pkg = "com.example", kind = NodeKind.METHOD)

        val index = NodeIndexFactory().create(listOf(nodeA, nodeB, nodeC))

        // A вызывает B и C
        val metaA = NodeMeta(
            ownerFqn = "com.example.Owner",
            imports = listOf("com.example.B", "com.example.C"),
            rawUsages = listOf(
                RawUsage.Simple("B", isCall = true),
                RawUsage.Simple("C", isCall = true),
            ),
        )

        // B вызывает A и C
        val metaB = NodeMeta(
            ownerFqn = "com.example.Owner",
            imports = listOf("com.example.A", "com.example.C"),
            rawUsages = listOf(
                RawUsage.Simple("A", isCall = true),
                RawUsage.Simple("C", isCall = true),
            ),
        )

        // C вызывает A и B
        val metaC = NodeMeta(
            ownerFqn = "com.example.Owner",
            imports = listOf("com.example.A", "com.example.B"),
            rawUsages = listOf(
                RawUsage.Simple("A", isCall = true),
                RawUsage.Simple("B", isCall = true),
            ),
        )

        val edgesA = CallEdgeLinker().link(nodeA, metaA, index)
        val edgesB = CallEdgeLinker().link(nodeB, metaB, index)
        val edgesC = CallEdgeLinker().link(nodeC, metaC, index)

        assertThat(edgesA).contains(Triple(nodeA, nodeB, EdgeKind.CALLS_CODE))
        assertThat(edgesA).contains(Triple(nodeA, nodeC, EdgeKind.CALLS_CODE))
        assertThat(edgesB).contains(Triple(nodeB, nodeA, EdgeKind.CALLS_CODE))
        assertThat(edgesB).contains(Triple(nodeB, nodeC, EdgeKind.CALLS_CODE))
        assertThat(edgesC).contains(Triple(nodeC, nodeA, EdgeKind.CALLS_CODE))
        assertThat(edgesC).contains(Triple(nodeC, nodeB, EdgeKind.CALLS_CODE))
    }

    @Test
    fun `link - Simple usage resolves all overloads via findMethodsByName`() {
        val owner = node(fqn = "com.example.Owner", name = "Owner", pkg = "com.example", kind = NodeKind.CLASS)
        val overload1 = node(fqn = "com.example.Owner.process(String)", name = "process", pkg = "com.example", kind = NodeKind.METHOD)
        val overload2 = node(fqn = "com.example.Owner.process(Int)", name = "process", pkg = "com.example", kind = NodeKind.METHOD)
        val caller = node(fqn = "com.example.Caller.run()", name = "run", pkg = "com.example", kind = NodeKind.METHOD)

        val index = NodeIndexFactory().create(listOf(owner, overload1, overload2, caller))
        val meta = NodeMeta(
            ownerFqn = "com.example.Owner",
            rawUsages = listOf(RawUsage.Simple("process", isCall = true)),
        )

        val edges = CallEdgeLinker().link(caller, meta, index)

        assertThat(edges).containsExactlyInAnyOrder(
            Triple(caller, overload1, EdgeKind.CALLS_CODE),
            Triple(caller, overload2, EdgeKind.CALLS_CODE),
        )
    }

    @Test
    fun `link - Dot usage resolves all overloads via findMethodsByName`() {
        val receiverType = node(fqn = "com.example.Service", name = "Service", pkg = "com.example", kind = NodeKind.CLASS)
        val overload1 = node(fqn = "com.example.Service.handle(String)", name = "handle", pkg = "com.example", kind = NodeKind.METHOD)
        val overload2 = node(fqn = "com.example.Service.handle(Int,String)", name = "handle", pkg = "com.example", kind = NodeKind.METHOD)
        val caller = node(fqn = "com.example.Caller.run()", name = "run", pkg = "com.example", kind = NodeKind.METHOD)

        val index = NodeIndexFactory().create(listOf(receiverType, overload1, overload2, caller))
        val meta = NodeMeta(
            imports = listOf("com.example.Service"),
            rawUsages = listOf(RawUsage.Dot(receiver = "Service", member = "handle", isCall = true)),
        )

        val edges = CallEdgeLinker().link(caller, meta, index)

        assertThat(edges).containsExactlyInAnyOrder(
            Triple(caller, overload1, EdgeKind.CALLS_CODE),
            Triple(caller, overload2, EdgeKind.CALLS_CODE),
        )
    }

    private fun node(
        fqn: String,
        name: String,
        pkg: String?,
        kind: NodeKind,
    ): Node =
        Node(
            id = null,
            application = app,
            fqn = fqn,
            name = name,
            packageName = pkg,
            kind = kind,
            lang = Lang.kotlin,
        )
}
