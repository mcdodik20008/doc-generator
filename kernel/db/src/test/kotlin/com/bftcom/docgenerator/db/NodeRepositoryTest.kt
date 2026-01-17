package com.bftcom.docgenerator.db

import com.bftcom.docgenerator.domain.application.Application
import com.bftcom.docgenerator.domain.enums.Lang
import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.domain.node.Node
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.PageRequest

class NodeRepositoryTest : BaseRepositoryTest() {

    @Autowired
    private lateinit var nodeRepository: NodeRepository

    @Autowired
    private lateinit var applicationRepository: ApplicationRepository

    private lateinit var application: Application

    @BeforeEach
    fun setUp() {
        application = Application(
            key = "test-app-${System.currentTimeMillis()}",
            name = "Test Application",
        )
        application = applicationRepository.save(application)
    }

    @Test
    fun `findByApplicationIdAndFqn - возвращает Node по applicationId и fqn`() {
        // Given
        val node = Node(
            application = application,
            fqn = "com.example.TestClass",
            name = "TestClass",
            packageName = "com.example",
            kind = NodeKind.CLASS,
            lang = Lang.kotlin,
        )
        nodeRepository.save(node)

        // When
        val found = nodeRepository.findByApplicationIdAndFqn(application.id!!, "com.example.TestClass")

        // Then
        assertThat(found).isNotNull
        assertThat(found!!.fqn).isEqualTo("com.example.TestClass")
        assertThat(found.name).isEqualTo("TestClass")
    }

    @Test
    fun `findAllByApplicationId - возвращает все Node для application`() {
        // Given
        val node1 = Node(
            application = application,
            fqn = "com.example.Class1",
            name = "Class1",
            kind = NodeKind.CLASS,
            lang = Lang.kotlin,
        )
        val node2 = Node(
            application = application,
            fqn = "com.example.Class2",
            name = "Class2",
            kind = NodeKind.CLASS,
            lang = Lang.kotlin,
        )
        nodeRepository.save(node1)
        nodeRepository.save(node2)

        // When
        val nodes = nodeRepository.findAllByApplicationId(application.id!!, PageRequest.of(0, 10))

        // Then
        assertThat(nodes).hasSizeGreaterThanOrEqualTo(2)
        assertThat(nodes.map { it.fqn }).contains("com.example.Class1", "com.example.Class2")
    }

    @Test
    fun `findAllByApplicationIdAndKindIn - фильтрует по kinds`() {
        // Given
        val classNode = Node(
            application = application,
            fqn = "com.example.Class",
            name = "Class",
            kind = NodeKind.CLASS,
            lang = Lang.kotlin,
        )
        val methodNode = Node(
            application = application,
            fqn = "com.example.method",
            name = "method",
            kind = NodeKind.METHOD,
            lang = Lang.kotlin,
            parent = classNode,
        )
        nodeRepository.save(classNode)
        nodeRepository.save(methodNode)

        // When
        val nodes = nodeRepository.findAllByApplicationIdAndKindIn(
            applicationId = application.id!!,
            kinds = setOf(NodeKind.CLASS),
            pageable = PageRequest.of(0, 10),
        )

        // Then
        assertThat(nodes).hasSize(1)
        assertThat(nodes[0].kind).isEqualTo(NodeKind.CLASS)
    }

    @Test
    fun `findAllByIdIn - возвращает Node по списку ID`() {
        // Given
        val node1 = Node(
            application = application,
            fqn = "com.example.Class1",
            kind = NodeKind.CLASS,
            lang = Lang.kotlin,
        )
        val node2 = Node(
            application = application,
            fqn = "com.example.Class2",
            kind = NodeKind.CLASS,
            lang = Lang.kotlin,
        )
        val saved1 = nodeRepository.save(node1)
        val saved2 = nodeRepository.save(node2)

        // When
        val nodes = nodeRepository.findAllByIdIn(setOf(saved1.id!!, saved2.id!!))

        // Then
        assertThat(nodes).hasSize(2)
        assertThat(nodes.map { it.id }).containsExactlyInAnyOrder(saved1.id, saved2.id)
    }

    @Test
    fun `findAllByParentId - возвращает дочерние Node`() {
        // Given
        val parent = Node(
            application = application,
            fqn = "com.example.Parent",
            name = "Parent",
            kind = NodeKind.CLASS,
            lang = Lang.kotlin,
        )
        val savedParent = nodeRepository.save(parent)

        val child1 = Node(
            application = application,
            fqn = "com.example.Parent.method1",
            name = "method1",
            kind = NodeKind.METHOD,
            lang = Lang.kotlin,
            parent = savedParent,
        )
        val child2 = Node(
            application = application,
            fqn = "com.example.Parent.method2",
            name = "method2",
            kind = NodeKind.METHOD,
            lang = Lang.kotlin,
            parent = savedParent,
        )
        nodeRepository.save(child1)
        nodeRepository.save(child2)

        // When
        val children = nodeRepository.findAllByParentId(savedParent.id!!)

        // Then
        assertThat(children).hasSize(2)
        assertThat(children.map { it.parent?.id }).containsOnly(savedParent.id)
    }

    @Test
    fun `countByApplicationId - возвращает количество Node для application`() {
        // Given
        val node1 = Node(
            application = application,
            fqn = "com.example.Class1",
            kind = NodeKind.CLASS,
            lang = Lang.kotlin,
        )
        val node2 = Node(
            application = application,
            fqn = "com.example.Class2",
            kind = NodeKind.CLASS,
            lang = Lang.kotlin,
        )
        nodeRepository.save(node1)
        nodeRepository.save(node2)

        // When
        val count = nodeRepository.countByApplicationId(application.id!!)

        // Then
        assertThat(count).isGreaterThanOrEqualTo(2)
    }

    @Test
    fun `lockNextMethodsWithoutDoc - возвращает методы без документации`() {
        // Given
        val method = Node(
            application = application,
            fqn = "com.example.method",
            name = "method",
            kind = NodeKind.METHOD,
            lang = Lang.kotlin,
        )
        nodeRepository.save(method)
        nodeRepository.flush()

        // When
        val methods = nodeRepository.lockNextMethodsWithoutDoc(locale = "ru", limit = 10)

        // Then
        assertThat(methods).isNotEmpty
        assertThat(methods.all { it.kind == NodeKind.METHOD }).isTrue
    }

    @Test
    fun `lockNextTypesWithoutDoc - возвращает типы без документации`() {
        // Given
        val classNode = Node(
            application = application,
            fqn = "com.example.Class",
            name = "Class",
            kind = NodeKind.CLASS,
            lang = Lang.kotlin,
        )
        nodeRepository.save(classNode)
        nodeRepository.flush()

        // When
        val types = nodeRepository.lockNextTypesWithoutDoc(locale = "ru", limit = 10)

        // Then
        assertThat(types).isNotEmpty
        assertThat(types.all { it.kind in setOf(NodeKind.CLASS, NodeKind.INTERFACE, NodeKind.ENUM, NodeKind.RECORD) }).isTrue
    }
}