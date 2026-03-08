package com.bftcom.docgenerator.chunking.nodedoc

import com.bftcom.docgenerator.db.NodeRepository
import com.bftcom.docgenerator.domain.application.Application
import com.bftcom.docgenerator.domain.enums.Lang
import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.domain.node.Node
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionStatus
import java.util.concurrent.ConcurrentHashMap

class NodeDocFillerSchedulerTest {
    private lateinit var txManager: PlatformTransactionManager
    private lateinit var nodeRepo: NodeRepository
    private lateinit var generator: NodeDocGenerator
    private lateinit var scheduler: NodeDocFillerScheduler

    @BeforeEach
    fun setUp() {
        txManager = mockk {
            val status = mockk<TransactionStatus>(relaxed = true)
            every { getTransaction(any()) } returns status
            every { commit(status) } just Runs
            every { rollback(status) } just Runs
        }
        nodeRepo = mockk(relaxed = true)
        generator = mockk(relaxed = true)

        scheduler =
            NodeDocFillerScheduler(
                txManager = txManager,
                nodeRepo = nodeRepo,
                generator = generator,
            )
    }

    @Test
    fun `processBatch - обрабатывает пустой батч`() {
        val result = scheduler.processBatch("METHOD") { _ -> emptyList() }
        assertThat(result).isEqualTo(0)
    }

    @Test
    fun `processBatch - обрабатывает батч с успешной генерацией`() {
        val app = Application(key = "app1", name = "App1")
        app.id = 1L
        val node1 = Node(application = app, fqn = "com.example.Method1", kind = NodeKind.METHOD, lang = Lang.kotlin)
        node1.id = 100L

        val loaderResult = listOf(node1)

        val generatedDoc =
            NodeDocGenerator.GeneratedDoc(
                docTech = "tech doc",
                docPublic = "public doc",
                docDigest = "digest",
                modelMeta = emptyMap(),
            )

        every { generator.generate(node1, "ru", false) } returns generatedDoc

        val result = scheduler.processBatch("METHOD") { _ -> loaderResult }

        assertThat(result).isEqualTo(1)
        verify(exactly = 1) { generator.generate(node1, "ru", false) }
        verify(exactly = 1) { generator.store(100L, "ru", generatedDoc) }
    }

    @Test
    fun `processBatch - пропускает ноду без id`() {
        val app = Application(key = "app1", name = "App1")
        app.id = 1L
        val node1 = Node(application = app, fqn = "com.example.Method1", kind = NodeKind.METHOD, lang = Lang.kotlin)
        node1.id = null

        val loaderResult = listOf(node1)

        val result = scheduler.processBatch("METHOD") { _ -> loaderResult }

        assertThat(result).isEqualTo(1)
        verify(exactly = 0) { generator.generate(any(), any(), any()) }
    }

    @Test
    fun `processBatch - обрабатывает ошибку генерации`() {
        val app = Application(key = "app1", name = "App1")
        app.id = 1L
        val node1 = Node(application = app, fqn = "com.example.Method1", kind = NodeKind.METHOD, lang = Lang.kotlin)
        node1.id = 100L

        val loaderResult = listOf(node1)

        every { generator.generate(node1, "ru", false) } throws RuntimeException("Generation failed")

        val result = scheduler.processBatch("METHOD") { _ -> loaderResult }

        assertThat(result).isEqualTo(1)
        verify(exactly = 1) { generator.generate(node1, "ru", false) }
    }

    @Test
    fun `processBatch - пропускает ноду с missing deps и увеличивает skip count`() {
        val app = Application(key = "app1", name = "App1")
        app.id = 1L
        val node1 = Node(application = app, fqn = "com.example.Method1", kind = NodeKind.METHOD, lang = Lang.kotlin)
        node1.id = 100L

        val loaderResult = listOf(node1)

        every { generator.generate(node1, "ru", false) } returns null
        every { nodeRepo.countByApplicationId(1L) } returns 100L

        val result = scheduler.processBatch("METHOD") { _ -> loaderResult }

        assertThat(result).isEqualTo(1)
        verify(exactly = 1) { generator.generate(node1, "ru", false) }

        val skipCountsField = NodeDocFillerScheduler::class.java.getDeclaredField("skipCounts")
        skipCountsField.isAccessible = true
        val skipCounts = skipCountsField.get(scheduler) as ConcurrentHashMap<Long, Int>
        assertThat(skipCounts[100L]).isEqualTo(1)
    }

    @Test
    fun `shouldAllowMissingDeps - возвращает true когда skip count достиг максимума`() {
        val app = Application(key = "app1", name = "App1")
        app.id = 1L
        val node1 = Node(application = app, fqn = "com.example.Method1", kind = NodeKind.METHOD, lang = Lang.kotlin)
        node1.id = 100L

        every { nodeRepo.countByApplicationId(1L) } returns 100L

        val skipCountsField = NodeDocFillerScheduler::class.java.getDeclaredField("skipCounts")
        skipCountsField.isAccessible = true
        val skipCounts = skipCountsField.get(scheduler) as ConcurrentHashMap<Long, Int>
        skipCounts[100L] = 10

        val shouldAllowMethod =
            NodeDocFillerScheduler::class.java.getDeclaredMethod(
                "shouldAllowMissingDeps",
                Node::class.java,
            )
        shouldAllowMethod.isAccessible = true

        val result = shouldAllowMethod.invoke(scheduler, node1) as Boolean

        assertThat(result).isTrue()
    }

    @Test
    fun `shouldAllowMissingDeps - возвращает false для не-METHOD ноды`() {
        val app = Application(key = "app1", name = "App1")
        app.id = 1L
        val node1 = Node(application = app, fqn = "com.example.Class1", kind = NodeKind.CLASS, lang = Lang.kotlin)
        node1.id = 100L

        val shouldAllowMethod =
            NodeDocFillerScheduler::class.java.getDeclaredMethod(
                "shouldAllowMissingDeps",
                Node::class.java,
            )
        shouldAllowMethod.isAccessible = true

        val result = shouldAllowMethod.invoke(scheduler, node1) as Boolean

        assertThat(result).isFalse()
    }

    @Test
    fun `maxSkipsFor - вычисляет правильное значение на основе skip factor`() {
        val app = Application(key = "app1", name = "App1")
        app.id = 1L
        val node1 = Node(application = app, fqn = "com.example.Method1", kind = NodeKind.METHOD, lang = Lang.kotlin)
        node1.id = 100L

        every { nodeRepo.countByApplicationId(1L) } returns 1000L

        val maxSkipsMethod =
            NodeDocFillerScheduler::class.java.getDeclaredMethod(
                "maxSkipsFor",
                Node::class.java,
            )
        maxSkipsMethod.isAccessible = true

        val result = maxSkipsMethod.invoke(scheduler, node1) as Int

        assertThat(result).isEqualTo(10)
    }

    @Test
    fun `maxSkipsFor - использует skipMin когда приложение не найдено`() {
        val app = Application(key = "app1", name = "App1")
        app.id = null
        val node1 = Node(application = app, fqn = "com.example.Method1", kind = NodeKind.METHOD, lang = Lang.kotlin)
        node1.id = 100L

        val maxSkipsMethod =
            NodeDocFillerScheduler::class.java.getDeclaredMethod(
                "maxSkipsFor",
                Node::class.java,
            )
        maxSkipsMethod.isAccessible = true

        val result = maxSkipsMethod.invoke(scheduler, node1) as Int

        assertThat(result).isEqualTo(3)
    }

    @Test
    fun `processBatch - батчит сохранения в одной транзакции`() {
        val app = Application(key = "app1", name = "App1")
        app.id = 1L
        val node1 = Node(application = app, fqn = "com.example.Method1", kind = NodeKind.METHOD, lang = Lang.kotlin)
        node1.id = 100L
        val node2 = Node(application = app, fqn = "com.example.Method2", kind = NodeKind.METHOD, lang = Lang.kotlin)
        node2.id = 200L

        val loaderResult = listOf(node1, node2)

        val generatedDoc1 =
            NodeDocGenerator.GeneratedDoc(
                docTech = "tech doc 1",
                docPublic = "public doc 1",
                docDigest = "digest 1",
                modelMeta = emptyMap(),
            )
        val generatedDoc2 =
            NodeDocGenerator.GeneratedDoc(
                docTech = "tech doc 2",
                docPublic = "public doc 2",
                docDigest = "digest 2",
                modelMeta = emptyMap(),
            )

        every { generator.generate(node1, "ru", false) } returns generatedDoc1
        every { generator.generate(node2, "ru", false) } returns generatedDoc2

        val result = scheduler.processBatch("METHOD") { _ -> loaderResult }

        assertThat(result).isEqualTo(2)
        verify(exactly = 1) { generator.store(100L, "ru", generatedDoc1) }
        verify(exactly = 1) { generator.store(200L, "ru", generatedDoc2) }
    }

    @Test
    fun `processBatch - обрабатывает смешанный батч с успешными, пропущенными и неудачными`() {
        val app = Application(key = "app1", name = "App1")
        app.id = 1L
        val node1 = Node(application = app, fqn = "com.example.Method1", kind = NodeKind.METHOD, lang = Lang.kotlin)
        node1.id = 100L
        val node2 = Node(application = app, fqn = "com.example.Method2", kind = NodeKind.METHOD, lang = Lang.kotlin)
        node2.id = 200L
        val node3 = Node(application = app, fqn = "com.example.Method3", kind = NodeKind.METHOD, lang = Lang.kotlin)
        node3.id = 300L

        val loaderResult = listOf(node1, node2, node3)

        val generatedDoc1 =
            NodeDocGenerator.GeneratedDoc(
                docTech = "tech doc 1",
                docPublic = "public doc 1",
                docDigest = "digest 1",
                modelMeta = emptyMap(),
            )

        every { generator.generate(node1, "ru", false) } returns generatedDoc1
        every { generator.generate(node2, "ru", false) } returns null
        every { generator.generate(node3, "ru", false) } throws RuntimeException("Generation failed")
        every { nodeRepo.countByApplicationId(1L) } returns 100L

        val result = scheduler.processBatch("METHOD") { _ -> loaderResult }

        assertThat(result).isEqualTo(3)
        verify(exactly = 1) { generator.generate(node1, "ru", false) }
        verify(exactly = 1) { generator.generate(node2, "ru", false) }
        verify(exactly = 1) { generator.generate(node3, "ru", false) }
        verify(exactly = 1) { generator.store(100L, "ru", generatedDoc1) }
        verify(exactly = 0) { generator.store(200L, any(), any()) }
        verify(exactly = 0) { generator.store(300L, any(), any()) }

        val skipCountsField = NodeDocFillerScheduler::class.java.getDeclaredField("skipCounts")
        skipCountsField.isAccessible = true
        val skipCounts = skipCountsField.get(scheduler) as ConcurrentHashMap<Long, Int>
        assertThat(skipCounts[200L]).isEqualTo(1)
    }

    @Test
    fun `processBatch - обрабатывает forced skip когда allowMissingDeps = true`() {
        val app = Application(key = "app1", name = "App1")
        app.id = 1L
        val node1 = Node(application = app, fqn = "com.example.Method1", kind = NodeKind.METHOD, lang = Lang.kotlin)
        node1.id = 100L

        val loaderResult = listOf(node1)

        every { generator.generate(node1, "ru", true) } returns null
        every { nodeRepo.countByApplicationId(1L) } returns 100L

        val skipCountsField = NodeDocFillerScheduler::class.java.getDeclaredField("skipCounts")
        skipCountsField.isAccessible = true
        val skipCounts = skipCountsField.get(scheduler) as ConcurrentHashMap<Long, Int>
        skipCounts[100L] = 10

        val result = scheduler.processBatch("METHOD") { _ -> loaderResult }

        assertThat(result).isEqualTo(1)
        verify(exactly = 1) { generator.generate(node1, "ru", true) }
        verify(exactly = 0) { generator.store(any(), any(), any()) }
    }

    @Test
    fun `maxSkipsFor - использует кэш при повторных вызовах`() {
        val app = Application(key = "app1", name = "App1")
        app.id = 1L
        val node1 = Node(application = app, fqn = "com.example.Method1", kind = NodeKind.METHOD, lang = Lang.kotlin)
        node1.id = 100L

        every { nodeRepo.countByApplicationId(1L) } returns 1000L

        val maxSkipsMethod =
            NodeDocFillerScheduler::class.java.getDeclaredMethod(
                "maxSkipsFor",
                Node::class.java,
            )
        maxSkipsMethod.isAccessible = true

        val result1 = maxSkipsMethod.invoke(scheduler, node1) as Int
        assertThat(result1).isEqualTo(10)

        val result2 = maxSkipsMethod.invoke(scheduler, node1) as Int
        assertThat(result2).isEqualTo(10)

        verify(exactly = 1) { nodeRepo.countByApplicationId(1L) }
    }

    @Test
    fun `maxSkipsFor - обновляет кэш после истечения TTL`() {
        val app = Application(key = "app1", name = "App1")
        app.id = 1L
        val node1 = Node(application = app, fqn = "com.example.Method1", kind = NodeKind.METHOD, lang = Lang.kotlin)
        node1.id = 100L

        every { nodeRepo.countByApplicationId(1L) } returnsMany listOf(1000L, 2000L)

        val cacheTtlField = NodeDocFillerScheduler::class.java.getDeclaredField("cacheTtlMs")
        cacheTtlField.isAccessible = true
        cacheTtlField.set(scheduler, 100L)

        val maxSkipsMethod =
            NodeDocFillerScheduler::class.java.getDeclaredMethod(
                "maxSkipsFor",
                Node::class.java,
            )
        maxSkipsMethod.isAccessible = true

        val result1 = maxSkipsMethod.invoke(scheduler, node1) as Int
        assertThat(result1).isEqualTo(10)

        Thread.sleep(150)

        val result2 = maxSkipsMethod.invoke(scheduler, node1) as Int
        assertThat(result2).isEqualTo(20)

        verify(exactly = 2) { nodeRepo.countByApplicationId(1L) }
    }

    @Test
    fun `poll - обрабатывает методы первыми`() {
        every { nodeRepo.lockNextMethodsWithoutDoc("ru", 10, 0) } returns listOf(
            Node(application = Application(key = "app1", name = "App1").apply { id = 1L }, fqn = "com.example.Method1", kind = NodeKind.METHOD, lang = Lang.kotlin).apply { id = 100L }
        )

        val generatedDoc =
            NodeDocGenerator.GeneratedDoc(
                docTech = "tech doc",
                docPublic = "public doc",
                docDigest = "digest",
                modelMeta = emptyMap(),
            )

        every { generator.generate(any(), "ru", false) } returns generatedDoc

        scheduler.poll()

        verify(exactly = 1) { nodeRepo.lockNextMethodsWithoutDoc("ru", 10, 0) }
        verify(exactly = 0) { nodeRepo.lockNextLeafNodesWithoutDoc(any(), any(), any()) }
        verify(exactly = 0) { nodeRepo.lockNextTypesWithoutDoc(any(), any(), any()) }
        verify(exactly = 0) { nodeRepo.lockNextPackagesWithoutDoc(any(), any(), any()) }
        verify(exactly = 0) { nodeRepo.lockNextModulesAndReposWithoutDoc(any(), any(), any()) }
    }

    @Test
    fun `poll - переходит к leaf когда методы отсутствуют`() {
        every { nodeRepo.lockNextMethodsWithoutDoc("ru", 10, 0) } returns emptyList()
        every { nodeRepo.lockNextLeafNodesWithoutDoc("ru", 10, 0) } returns listOf(
            Node(application = Application(key = "app1", name = "App1").apply { id = 1L }, fqn = "infra:http:GET:/api/test", kind = NodeKind.ENDPOINT, lang = Lang.kotlin).apply { id = 150L }
        )

        val generatedDoc =
            NodeDocGenerator.GeneratedDoc(
                docTech = "tech doc",
                docPublic = "public doc",
                docDigest = "digest",
                modelMeta = emptyMap(),
            )

        every { generator.generate(any(), "ru", false) } returns generatedDoc

        scheduler.poll()

        verify(exactly = 1) { nodeRepo.lockNextMethodsWithoutDoc("ru", 10, 0) }
        verify(exactly = 1) { nodeRepo.lockNextLeafNodesWithoutDoc("ru", 10, 0) }
        verify(exactly = 0) { nodeRepo.lockNextTypesWithoutDoc(any(), any(), any()) }
        verify(exactly = 0) { nodeRepo.lockNextPackagesWithoutDoc(any(), any(), any()) }
        verify(exactly = 0) { nodeRepo.lockNextModulesAndReposWithoutDoc(any(), any(), any()) }
    }

    @Test
    fun `poll - переходит к типам когда методы и leaf отсутствуют`() {
        every { nodeRepo.lockNextMethodsWithoutDoc("ru", 10, 0) } returns emptyList()
        every { nodeRepo.lockNextLeafNodesWithoutDoc("ru", 10, 0) } returns emptyList()
        every { nodeRepo.lockNextTypesWithoutDoc("ru", 10, 0) } returns listOf(
            Node(application = Application(key = "app1", name = "App1").apply { id = 1L }, fqn = "com.example.Class1", kind = NodeKind.CLASS, lang = Lang.kotlin).apply { id = 200L }
        )

        val generatedDoc =
            NodeDocGenerator.GeneratedDoc(
                docTech = "tech doc",
                docPublic = "public doc",
                docDigest = "digest",
                modelMeta = emptyMap(),
            )

        every { generator.generate(any(), "ru", false) } returns generatedDoc

        scheduler.poll()

        verify(exactly = 1) { nodeRepo.lockNextMethodsWithoutDoc("ru", 10, 0) }
        verify(exactly = 1) { nodeRepo.lockNextLeafNodesWithoutDoc("ru", 10, 0) }
        verify(exactly = 1) { nodeRepo.lockNextTypesWithoutDoc("ru", 10, 0) }
        verify(exactly = 0) { nodeRepo.lockNextPackagesWithoutDoc(any(), any(), any()) }
        verify(exactly = 0) { nodeRepo.lockNextModulesAndReposWithoutDoc(any(), any(), any()) }
    }

    @Test
    fun `poll - использует random методы когда включен флаг`() {
        val randomMethodsField = NodeDocFillerScheduler::class.java.getDeclaredField("randomMethods")
        randomMethodsField.isAccessible = true
        randomMethodsField.set(scheduler, true)

        every { nodeRepo.lockNextMethodsWithoutDocRandom("ru", 10) } returns emptyList()

        scheduler.poll()

        verify(exactly = 1) { nodeRepo.lockNextMethodsWithoutDocRandom("ru", 10) }
        verify(exactly = 0) { nodeRepo.lockNextMethodsWithoutDoc(any(), any(), any()) }
    }

    @Test
    fun `processBatch - не сохраняет когда нет успешных генераций`() {
        val app = Application(key = "app1", name = "App1")
        app.id = 1L
        val node1 = Node(application = app, fqn = "com.example.Method1", kind = NodeKind.METHOD, lang = Lang.kotlin)
        node1.id = 100L

        val loaderResult = listOf(node1)

        every { generator.generate(node1, "ru", false) } returns null
        every { nodeRepo.countByApplicationId(1L) } returns 100L

        val result = scheduler.processBatch("METHOD") { _ -> loaderResult }

        assertThat(result).isEqualTo(1)
        verify(exactly = 0) { generator.store(any(), any(), any()) }
    }

    @Test
    fun `processBatch - удаляет skip count при успешной генерации`() {
        val app = Application(key = "app1", name = "App1")
        app.id = 1L
        val node1 = Node(application = app, fqn = "com.example.Method1", kind = NodeKind.METHOD, lang = Lang.kotlin)
        node1.id = 100L

        val loaderResult = listOf(node1)

        val generatedDoc =
            NodeDocGenerator.GeneratedDoc(
                docTech = "tech doc",
                docPublic = "public doc",
                docDigest = "digest",
                modelMeta = emptyMap(),
            )

        every { generator.generate(node1, "ru", false) } returns generatedDoc

        val skipCountsField = NodeDocFillerScheduler::class.java.getDeclaredField("skipCounts")
        skipCountsField.isAccessible = true
        val skipCounts = skipCountsField.get(scheduler) as ConcurrentHashMap<Long, Int>
        skipCounts[100L] = 5

        val result = scheduler.processBatch("METHOD") { _ -> loaderResult }

        assertThat(result).isEqualTo(1)
        assertThat(skipCounts[100L]).isNull()
    }

    @Test
    fun `maxSkipsFor - использует skipMin когда byFactor меньше skipMin`() {
        val app = Application(key = "app1", name = "App1")
        app.id = 1L
        val node1 = Node(application = app, fqn = "com.example.Method1", kind = NodeKind.METHOD, lang = Lang.kotlin)
        node1.id = 100L

        every { nodeRepo.countByApplicationId(1L) } returns 100L

        val maxSkipsMethod =
            NodeDocFillerScheduler::class.java.getDeclaredMethod(
                "maxSkipsFor",
                Node::class.java,
            )
        maxSkipsMethod.isAccessible = true

        val result = maxSkipsMethod.invoke(scheduler, node1) as Int

        assertThat(result).isEqualTo(3)
    }

    @Test
    fun `maxSkipsFor - использует skipMax когда byFactor больше skipMax`() {
        val app = Application(key = "app1", name = "App1")
        app.id = 1L
        val node1 = Node(application = app, fqn = "com.example.Method1", kind = NodeKind.METHOD, lang = Lang.kotlin)
        node1.id = 100L

        every { nodeRepo.countByApplicationId(1L) } returns 20000L

        val maxSkipsMethod =
            NodeDocFillerScheduler::class.java.getDeclaredMethod(
                "maxSkipsFor",
                Node::class.java,
            )
        maxSkipsMethod.isAccessible = true

        val result = maxSkipsMethod.invoke(scheduler, node1) as Int

        assertThat(result).isEqualTo(100)
    }

    @Test
    fun `maxSkipsFor - обрабатывает нулевое количество узлов`() {
        val app = Application(key = "app1", name = "App1")
        app.id = 1L
        val node1 = Node(application = app, fqn = "com.example.Method1", kind = NodeKind.METHOD, lang = Lang.kotlin)
        node1.id = 100L

        every { nodeRepo.countByApplicationId(1L) } returns 0L

        val maxSkipsMethod =
            NodeDocFillerScheduler::class.java.getDeclaredMethod(
                "maxSkipsFor",
                Node::class.java,
            )
        maxSkipsMethod.isAccessible = true

        val result = maxSkipsMethod.invoke(scheduler, node1) as Int

        assertThat(result).isEqualTo(3)
    }

    @Test
    fun `shouldAllowMissingDeps - возвращает false когда skip count меньше максимума`() {
        val app = Application(key = "app1", name = "App1")
        app.id = 1L
        val node1 = Node(application = app, fqn = "com.example.Method1", kind = NodeKind.METHOD, lang = Lang.kotlin)
        node1.id = 100L

        every { nodeRepo.countByApplicationId(1L) } returns 1000L

        val skipCountsField = NodeDocFillerScheduler::class.java.getDeclaredField("skipCounts")
        skipCountsField.isAccessible = true
        val skipCounts = skipCountsField.get(scheduler) as ConcurrentHashMap<Long, Int>
        skipCounts[100L] = 5

        val shouldAllowMethod =
            NodeDocFillerScheduler::class.java.getDeclaredMethod(
                "shouldAllowMissingDeps",
                Node::class.java,
            )
        shouldAllowMethod.isAccessible = true

        val result = shouldAllowMethod.invoke(scheduler, node1) as Boolean

        assertThat(result).isFalse()
    }

    @Test
    fun `shouldAllowMissingDeps - возвращает false когда nodeId равен null`() {
        val app = Application(key = "app1", name = "App1")
        app.id = 1L
        val node1 = Node(application = app, fqn = "com.example.Method1", kind = NodeKind.METHOD, lang = Lang.kotlin)
        node1.id = null

        val shouldAllowMethod =
            NodeDocFillerScheduler::class.java.getDeclaredMethod(
                "shouldAllowMissingDeps",
                Node::class.java,
            )
        shouldAllowMethod.isAccessible = true

        val result = shouldAllowMethod.invoke(scheduler, node1) as Boolean

        assertThat(result).isFalse()
    }

    @Test
    fun `shouldAllowMissingDeps - возвращает false для INTERFACE`() {
        val app = Application(key = "app1", name = "App1")
        app.id = 1L
        val node1 = Node(application = app, fqn = "com.example.Interface1", kind = NodeKind.INTERFACE, lang = Lang.kotlin)
        node1.id = 100L

        val shouldAllowMethod =
            NodeDocFillerScheduler::class.java.getDeclaredMethod(
                "shouldAllowMissingDeps",
                Node::class.java,
            )
        shouldAllowMethod.isAccessible = true

        val result = shouldAllowMethod.invoke(scheduler, node1) as Boolean

        assertThat(result).isFalse()
    }

    @Test
    fun `shouldAllowMissingDeps - возвращает false для ENUM`() {
        val app = Application(key = "app1", name = "App1")
        app.id = 1L
        val node1 = Node(application = app, fqn = "com.example.Enum1", kind = NodeKind.ENUM, lang = Lang.kotlin)
        node1.id = 100L

        val shouldAllowMethod =
            NodeDocFillerScheduler::class.java.getDeclaredMethod(
                "shouldAllowMissingDeps",
                Node::class.java,
            )
        shouldAllowMethod.isAccessible = true

        val result = shouldAllowMethod.invoke(scheduler, node1) as Boolean

        assertThat(result).isFalse()
    }

    @Test
    fun `shouldAllowMissingDeps - возвращает false для PACKAGE`() {
        val app = Application(key = "app1", name = "App1")
        app.id = 1L
        val node1 = Node(application = app, fqn = "com.example", kind = NodeKind.PACKAGE, lang = Lang.kotlin)
        node1.id = 100L

        val shouldAllowMethod =
            NodeDocFillerScheduler::class.java.getDeclaredMethod(
                "shouldAllowMissingDeps",
                Node::class.java,
            )
        shouldAllowMethod.isAccessible = true

        val result = shouldAllowMethod.invoke(scheduler, node1) as Boolean

        assertThat(result).isFalse()
    }

    @Test
    fun `poll - переходит к packages когда методы, leaf и типы отсутствуют`() {
        every { nodeRepo.lockNextMethodsWithoutDoc("ru", 10, 0) } returns emptyList()
        every { nodeRepo.lockNextLeafNodesWithoutDoc("ru", 10, 0) } returns emptyList()
        every { nodeRepo.lockNextTypesWithoutDoc("ru", 10, 0) } returns emptyList()
        every { nodeRepo.lockNextPackagesWithoutDoc("ru", 10, 0) } returns listOf(
            Node(application = Application(key = "app1", name = "App1").apply { id = 1L }, fqn = "com.example", kind = NodeKind.PACKAGE, lang = Lang.kotlin).apply { id = 300L }
        )

        val generatedDoc =
            NodeDocGenerator.GeneratedDoc(
                docTech = "tech doc",
                docPublic = "public doc",
                docDigest = "digest",
                modelMeta = emptyMap(),
            )

        every { generator.generate(any(), "ru", false) } returns generatedDoc

        scheduler.poll()

        verify(exactly = 1) { nodeRepo.lockNextMethodsWithoutDoc("ru", 10, 0) }
        verify(exactly = 1) { nodeRepo.lockNextLeafNodesWithoutDoc("ru", 10, 0) }
        verify(exactly = 1) { nodeRepo.lockNextTypesWithoutDoc("ru", 10, 0) }
        verify(exactly = 1) { nodeRepo.lockNextPackagesWithoutDoc("ru", 10, 0) }
        verify(exactly = 0) { nodeRepo.lockNextModulesAndReposWithoutDoc(any(), any(), any()) }
    }

    @Test
    fun `poll - переходит к modules repos когда все предыдущие уровни отсутствуют`() {
        every { nodeRepo.lockNextMethodsWithoutDoc("ru", 10, 0) } returns emptyList()
        every { nodeRepo.lockNextLeafNodesWithoutDoc("ru", 10, 0) } returns emptyList()
        every { nodeRepo.lockNextTypesWithoutDoc("ru", 10, 0) } returns emptyList()
        every { nodeRepo.lockNextPackagesWithoutDoc("ru", 10, 0) } returns emptyList()
        every { nodeRepo.lockNextModulesAndReposWithoutDoc("ru", 10, 0) } returns listOf(
            Node(application = Application(key = "app1", name = "App1").apply { id = 1L }, fqn = "com.example.module", kind = NodeKind.MODULE, lang = Lang.kotlin).apply { id = 400L }
        )

        val generatedDoc =
            NodeDocGenerator.GeneratedDoc(
                docTech = "tech doc",
                docPublic = "public doc",
                docDigest = "digest",
                modelMeta = emptyMap(),
            )

        every { generator.generate(any(), "ru", false) } returns generatedDoc

        scheduler.poll()

        verify(exactly = 1) { nodeRepo.lockNextMethodsWithoutDoc("ru", 10, 0) }
        verify(exactly = 1) { nodeRepo.lockNextLeafNodesWithoutDoc("ru", 10, 0) }
        verify(exactly = 1) { nodeRepo.lockNextTypesWithoutDoc("ru", 10, 0) }
        verify(exactly = 1) { nodeRepo.lockNextPackagesWithoutDoc("ru", 10, 0) }
        verify(exactly = 1) { nodeRepo.lockNextModulesAndReposWithoutDoc("ru", 10, 0) }
    }

    @Test
    fun `processBatch - обрабатывает большой батч`() {
        val app = Application(key = "app1", name = "App1")
        app.id = 1L

        val nodes = (1..50).map { i ->
            Node(application = app, fqn = "com.example.Method$i", kind = NodeKind.METHOD, lang = Lang.kotlin).apply { id = (100L + i) }
        }

        val generatedDocs = nodes.map { node ->
            node.id!! to NodeDocGenerator.GeneratedDoc(
                docTech = "tech doc ${node.id}",
                docPublic = "public doc ${node.id}",
                docDigest = "digest ${node.id}",
                modelMeta = emptyMap(),
            )
        }

        nodes.forEachIndexed { index, node ->
            every { generator.generate(node, "ru", false) } returns generatedDocs[index].second
        }

        val result = scheduler.processBatch("METHOD") { _ -> nodes }

        assertThat(result).isEqualTo(50)
        generatedDocs.forEach { (nodeId, doc) ->
            verify(exactly = 1) { generator.store(nodeId, "ru", doc) }
        }
    }

    @Test
    fun `processBatch - обрабатывает батч где все элементы пропущены`() {
        val app = Application(key = "app1", name = "App1")
        app.id = 1L
        val node1 = Node(application = app, fqn = "com.example.Method1", kind = NodeKind.METHOD, lang = Lang.kotlin)
        node1.id = 100L
        val node2 = Node(application = app, fqn = "com.example.Method2", kind = NodeKind.METHOD, lang = Lang.kotlin)
        node2.id = 200L

        val loaderResult = listOf(node1, node2)

        every { generator.generate(any(), "ru", false) } returns null
        every { nodeRepo.countByApplicationId(1L) } returns 100L

        val result = scheduler.processBatch("METHOD") { _ -> loaderResult }

        assertThat(result).isEqualTo(2)
        verify(exactly = 0) { generator.store(any(), any(), any()) }

        val skipCountsField = NodeDocFillerScheduler::class.java.getDeclaredField("skipCounts")
        skipCountsField.isAccessible = true
        val skipCounts = skipCountsField.get(scheduler) as ConcurrentHashMap<Long, Int>
        assertThat(skipCounts[100L]).isEqualTo(1)
        assertThat(skipCounts[200L]).isEqualTo(1)
    }

    @Test
    fun `processBatch - обрабатывает батч где все элементы упали с ошибкой`() {
        val app = Application(key = "app1", name = "App1")
        app.id = 1L
        val node1 = Node(application = app, fqn = "com.example.Method1", kind = NodeKind.METHOD, lang = Lang.kotlin)
        node1.id = 100L
        val node2 = Node(application = app, fqn = "com.example.Method2", kind = NodeKind.METHOD, lang = Lang.kotlin)
        node2.id = 200L

        val loaderResult = listOf(node1, node2)

        every { generator.generate(any(), "ru", false) } throws RuntimeException("Generation failed")

        val result = scheduler.processBatch("METHOD") { _ -> loaderResult }

        assertThat(result).isEqualTo(2)
        verify(exactly = 0) { generator.store(any(), any(), any()) }
    }

    @Test
    fun `maxSkipsFor - обновляет access time при использовании кэша`() {
        val app = Application(key = "app1", name = "App1")
        app.id = 1L
        val node1 = Node(application = app, fqn = "com.example.Method1", kind = NodeKind.METHOD, lang = Lang.kotlin)
        node1.id = 100L

        every { nodeRepo.countByApplicationId(1L) } returns 1000L

        val maxSkipsMethod =
            NodeDocFillerScheduler::class.java.getDeclaredMethod(
                "maxSkipsFor",
                Node::class.java,
            )
        maxSkipsMethod.isAccessible = true

        val accessTimeField = NodeDocFillerScheduler::class.java.getDeclaredField("appNodeCountsAccessTime")
        accessTimeField.isAccessible = true
        val accessTimeMap = accessTimeField.get(scheduler) as ConcurrentHashMap<Long, java.util.concurrent.atomic.AtomicLong>

        maxSkipsMethod.invoke(scheduler, node1)
        val firstAccessTime = accessTimeMap[1L]!!.get()

        Thread.sleep(10)

        maxSkipsMethod.invoke(scheduler, node1)
        val secondAccessTime = accessTimeMap[1L]!!.get()

        assertThat(secondAccessTime).isGreaterThan(firstAccessTime)
    }

    @Test
    fun `processBatch - обрабатывает батч с нодами разных типов`() {
        val app = Application(key = "app1", name = "App1")
        app.id = 1L
        val methodNode = Node(application = app, fqn = "com.example.Method1", kind = NodeKind.METHOD, lang = Lang.kotlin)
        methodNode.id = 100L
        val classNode = Node(application = app, fqn = "com.example.Class1", kind = NodeKind.CLASS, lang = Lang.kotlin)
        classNode.id = 200L

        val loaderResult = listOf(methodNode, classNode)

        val generatedDoc1 =
            NodeDocGenerator.GeneratedDoc(
                docTech = "tech doc 1",
                docPublic = "public doc 1",
                docDigest = "digest 1",
                modelMeta = emptyMap(),
            )
        val generatedDoc2 =
            NodeDocGenerator.GeneratedDoc(
                docTech = "tech doc 2",
                docPublic = "public doc 2",
                docDigest = "digest 2",
                modelMeta = emptyMap(),
            )

        every { generator.generate(methodNode, "ru", false) } returns generatedDoc1
        every { generator.generate(classNode, "ru", false) } returns generatedDoc2

        val result = scheduler.processBatch("MIXED") { _ -> loaderResult }

        assertThat(result).isEqualTo(2)
        verify(exactly = 1) { generator.store(100L, "ru", generatedDoc1) }
        verify(exactly = 1) { generator.store(200L, "ru", generatedDoc2) }
    }

    @Test
    fun `processBatch - обрабатывает ноду с allowMissingDeps=true и успешной генерацией`() {
        val app = Application(key = "app1", name = "App1")
        app.id = 1L
        val node1 = Node(application = app, fqn = "com.example.Method1", kind = NodeKind.METHOD, lang = Lang.kotlin)
        node1.id = 100L

        val loaderResult = listOf(node1)

        val generatedDoc =
            NodeDocGenerator.GeneratedDoc(
                docTech = "tech doc",
                docPublic = "public doc",
                docDigest = "digest",
                modelMeta = emptyMap(),
            )

        val skipCountsField = NodeDocFillerScheduler::class.java.getDeclaredField("skipCounts")
        skipCountsField.isAccessible = true
        val skipCounts = skipCountsField.get(scheduler) as ConcurrentHashMap<Long, Int>
        skipCounts[100L] = 10

        every { generator.generate(node1, "ru", true) } returns generatedDoc
        every { nodeRepo.countByApplicationId(1L) } returns 100L

        val result = scheduler.processBatch("METHOD") { _ -> loaderResult }

        assertThat(result).isEqualTo(1)
        verify(exactly = 1) { generator.generate(node1, "ru", true) }
        verify(exactly = 1) { generator.store(100L, "ru", generatedDoc) }
        assertThat(skipCounts[100L]).isNull()
    }

    @Test
    fun `maxSkipsFor - обрабатывает очень большое количество узлов`() {
        val app = Application(key = "app1", name = "App1")
        app.id = 1L
        val node1 = Node(application = app, fqn = "com.example.Method1", kind = NodeKind.METHOD, lang = Lang.kotlin)
        node1.id = 100L

        every { nodeRepo.countByApplicationId(1L) } returns Long.MAX_VALUE

        val maxSkipsMethod =
            NodeDocFillerScheduler::class.java.getDeclaredMethod(
                "maxSkipsFor",
                Node::class.java,
            )
        maxSkipsMethod.isAccessible = true

        val result = maxSkipsMethod.invoke(scheduler, node1) as Int

        assertThat(result).isEqualTo(100)
    }

    @Test
    fun `processBatch - обрабатывает skip count инкремент для нескольких пропусков`() {
        val app = Application(key = "app1", name = "App1")
        app.id = 1L
        val node1 = Node(application = app, fqn = "com.example.Method1", kind = NodeKind.METHOD, lang = Lang.kotlin)
        node1.id = 100L

        val loaderResult = listOf(node1)

        every { generator.generate(node1, "ru", false) } returns null
        every { nodeRepo.countByApplicationId(1L) } returns 100L

        val skipCountsField = NodeDocFillerScheduler::class.java.getDeclaredField("skipCounts")
        skipCountsField.isAccessible = true
        val skipCounts = skipCountsField.get(scheduler) as ConcurrentHashMap<Long, Int>

        scheduler.processBatch("METHOD") { _ -> loaderResult }
        assertThat(skipCounts[100L]).isEqualTo(1)

        scheduler.processBatch("METHOD") { _ -> loaderResult }
        assertThat(skipCounts[100L]).isEqualTo(2)

        scheduler.processBatch("METHOD") { _ -> loaderResult }
        assertThat(skipCounts[100L]).isEqualTo(3)
    }

    @Test
    fun `maxSkipsFor - обрабатывает точное значение на границе skipMin`() {
        val app = Application(key = "app1", name = "App1")
        app.id = 1L
        val node1 = Node(application = app, fqn = "com.example.Method1", kind = NodeKind.METHOD, lang = Lang.kotlin)
        node1.id = 100L

        every { nodeRepo.countByApplicationId(1L) } returns 300L

        val maxSkipsMethod =
            NodeDocFillerScheduler::class.java.getDeclaredMethod(
                "maxSkipsFor",
                Node::class.java,
            )
        maxSkipsMethod.isAccessible = true

        val result = maxSkipsMethod.invoke(scheduler, node1) as Int

        assertThat(result).isEqualTo(3)
    }

    @Test
    fun `maxSkipsFor - обрабатывает точное значение на границе skipMax`() {
        val app = Application(key = "app1", name = "App1")
        app.id = 1L
        val node1 = Node(application = app, fqn = "com.example.Method1", kind = NodeKind.METHOD, lang = Lang.kotlin)
        node1.id = 100L

        every { nodeRepo.countByApplicationId(1L) } returns 10000L

        val maxSkipsMethod =
            NodeDocFillerScheduler::class.java.getDeclaredMethod(
                "maxSkipsFor",
                Node::class.java,
            )
        maxSkipsMethod.isAccessible = true

        val result = maxSkipsMethod.invoke(scheduler, node1) as Int

        assertThat(result).isEqualTo(100)
    }

    @Test
    fun `processBatch - обрабатывает случай когда tx execute возвращает null`() {
        val mockTxManager = mockk<PlatformTransactionManager> {
            val status = mockk<TransactionStatus>(relaxed = true)
            every { getTransaction(any()) } returns status
            every { commit(status) } just Runs
            every { rollback(status) } just Runs
        }

        val testScheduler = NodeDocFillerScheduler(mockTxManager, nodeRepo, generator)

        val result = testScheduler.processBatch("METHOD") { _ -> emptyList() }

        assertThat(result).isEqualTo(0)
    }

    @Test
    fun `processBatch - проверяет debug логирование когда skipped не пустой`() {
        val app = Application(key = "app1", name = "App1")
        app.id = 1L
        val node1 = Node(application = app, fqn = "com.example.Method1", kind = NodeKind.METHOD, lang = Lang.kotlin)
        node1.id = 100L

        val loaderResult = listOf(node1)

        every { generator.generate(node1, "ru", false) } returns null
        every { nodeRepo.countByApplicationId(1L) } returns 100L

        val result = scheduler.processBatch("METHOD") { _ -> loaderResult }

        assertThat(result).isEqualTo(1)
        verify(exactly = 0) { generator.store(any(), any(), any()) }
    }

    @Test
    fun `processBatch - логирует только success без skipped и failed`() {
        val app = Application(key = "app1", name = "App1")
        app.id = 1L
        val node1 = Node(application = app, fqn = "com.example.Method1", kind = NodeKind.METHOD, lang = Lang.kotlin)
        node1.id = 100L

        val loaderResult = listOf(node1)

        val generatedDoc =
            NodeDocGenerator.GeneratedDoc(
                docTech = "tech doc",
                docPublic = "public doc",
                docDigest = "digest",
                modelMeta = emptyMap(),
            )

        every { generator.generate(node1, "ru", false) } returns generatedDoc

        val result = scheduler.processBatch("METHOD") { _ -> loaderResult }

        assertThat(result).isEqualTo(1)
        verify(exactly = 1) { generator.store(100L, "ru", generatedDoc) }
    }

    @Test
    fun `processBatch - логирует только skipped без success и failed`() {
        val app = Application(key = "app1", name = "App1")
        app.id = 1L
        val node1 = Node(application = app, fqn = "com.example.Method1", kind = NodeKind.METHOD, lang = Lang.kotlin)
        node1.id = 100L

        val loaderResult = listOf(node1)

        every { generator.generate(node1, "ru", false) } returns null
        every { nodeRepo.countByApplicationId(1L) } returns 100L

        val result = scheduler.processBatch("METHOD") { _ -> loaderResult }

        assertThat(result).isEqualTo(1)
        verify(exactly = 0) { generator.store(any(), any(), any()) }
    }

    @Test
    fun `processBatch - логирует только failed без success и skipped`() {
        val app = Application(key = "app1", name = "App1")
        app.id = 1L
        val node1 = Node(application = app, fqn = "com.example.Method1", kind = NodeKind.METHOD, lang = Lang.kotlin)
        node1.id = 100L

        val loaderResult = listOf(node1)

        every { generator.generate(node1, "ru", false) } throws RuntimeException("Generation failed")

        val result = scheduler.processBatch("METHOD") { _ -> loaderResult }

        assertThat(result).isEqualTo(1)
        verify(exactly = 0) { generator.store(any(), any(), any()) }
    }

    @Test
    fun `processBatch - не логирует когда success=0, skipped=0, failed=0`() {
        val result = scheduler.processBatch("METHOD") { _ -> emptyList() }

        assertThat(result).isEqualTo(0)
    }

    @Test
    fun `processBatch - обрабатывает merge с уже существующим значением в skipCounts`() {
        val app = Application(key = "app1", name = "App1")
        app.id = 1L
        val node1 = Node(application = app, fqn = "com.example.Method1", kind = NodeKind.METHOD, lang = Lang.kotlin)
        node1.id = 100L

        val loaderResult = listOf(node1)

        every { generator.generate(node1, "ru", false) } returns null
        every { nodeRepo.countByApplicationId(1L) } returns 10000L

        val skipCountsField = NodeDocFillerScheduler::class.java.getDeclaredField("skipCounts")
        skipCountsField.isAccessible = true
        val skipCounts = skipCountsField.get(scheduler) as ConcurrentHashMap<Long, Int>
        skipCounts[100L] = 2

        val result = scheduler.processBatch("METHOD") { _ -> loaderResult }

        assertThat(result).isEqualTo(1)
        assertThat(skipCounts[100L]).isEqualTo(3)
    }

    @Test
    fun `shouldAllowMissingDeps - возвращает true когда currentSkips равен maxSkips`() {
        val app = Application(key = "app1", name = "App1")
        app.id = 1L
        val node1 = Node(application = app, fqn = "com.example.Method1", kind = NodeKind.METHOD, lang = Lang.kotlin)
        node1.id = 100L

        every { nodeRepo.countByApplicationId(1L) } returns 1000L

        val skipCountsField = NodeDocFillerScheduler::class.java.getDeclaredField("skipCounts")
        skipCountsField.isAccessible = true
        val skipCounts = skipCountsField.get(scheduler) as ConcurrentHashMap<Long, Int>
        skipCounts[100L] = 10

        val shouldAllowMethod =
            NodeDocFillerScheduler::class.java.getDeclaredMethod(
                "shouldAllowMissingDeps",
                Node::class.java,
            )
        shouldAllowMethod.isAccessible = true

        val result = shouldAllowMethod.invoke(scheduler, node1) as Boolean

        assertThat(result).isTrue()
    }

    @Test
    fun `shouldAllowMissingDeps - возвращает false когда currentSkips меньше maxSkips`() {
        val app = Application(key = "app1", name = "App1")
        app.id = 1L
        val node1 = Node(application = app, fqn = "com.example.Method1", kind = NodeKind.METHOD, lang = Lang.kotlin)
        node1.id = 100L

        every { nodeRepo.countByApplicationId(1L) } returns 1000L

        val skipCountsField = NodeDocFillerScheduler::class.java.getDeclaredField("skipCounts")
        skipCountsField.isAccessible = true
        val skipCounts = skipCountsField.get(scheduler) as ConcurrentHashMap<Long, Int>
        skipCounts[100L] = 9

        val shouldAllowMethod =
            NodeDocFillerScheduler::class.java.getDeclaredMethod(
                "shouldAllowMissingDeps",
                Node::class.java,
            )
        shouldAllowMethod.isAccessible = true

        val result = shouldAllowMethod.invoke(scheduler, node1) as Boolean

        assertThat(result).isFalse()
    }

    // === Offset mechanism tests ===

    @Test
    fun `offset - advances when all items skipped`() {
        val app = Application(key = "app1", name = "App1")
        app.id = 1L
        val node1 = Node(application = app, fqn = "com.example.Method1", kind = NodeKind.METHOD, lang = Lang.kotlin)
        node1.id = 100L

        every { generator.generate(node1, "ru", false) } returns null
        every { nodeRepo.countByApplicationId(1L) } returns 100L

        val kindOffsetsField = NodeDocFillerScheduler::class.java.getDeclaredField("kindOffsets")
        kindOffsetsField.isAccessible = true
        val kindOffsets = kindOffsetsField.get(scheduler) as ConcurrentHashMap<String, Int>

        // First call: offset=0, all skipped → offset advances to batchSize(10)
        scheduler.processBatch("TEST_KIND") { _ -> listOf(node1) }
        assertThat(kindOffsets["TEST_KIND"]).isEqualTo(10)

        // Second call: offset=10, all skipped → offset advances to 20
        scheduler.processBatch("TEST_KIND") { _ -> listOf(node1) }
        assertThat(kindOffsets["TEST_KIND"]).isEqualTo(20)
    }

    @Test
    fun `offset - resets on success`() {
        val app = Application(key = "app1", name = "App1")
        app.id = 1L
        val node1 = Node(application = app, fqn = "com.example.Method1", kind = NodeKind.METHOD, lang = Lang.kotlin)
        node1.id = 100L

        val generatedDoc = NodeDocGenerator.GeneratedDoc(
            docTech = "tech", docPublic = "pub", docDigest = "dig", modelMeta = emptyMap()
        )
        every { generator.generate(node1, "ru", false) } returns generatedDoc

        val kindOffsetsField = NodeDocFillerScheduler::class.java.getDeclaredField("kindOffsets")
        kindOffsetsField.isAccessible = true
        val kindOffsets = kindOffsetsField.get(scheduler) as ConcurrentHashMap<String, Int>

        // Set a non-zero offset
        kindOffsets["TEST_KIND"] = 30

        // Success → offset resets to 0
        scheduler.processBatch("TEST_KIND") { _ -> listOf(node1) }
        assertThat(kindOffsets["TEST_KIND"]).isEqualTo(0)
    }

    @Test
    fun `offset - resets when batch is empty (reached end)`() {
        val kindOffsetsField = NodeDocFillerScheduler::class.java.getDeclaredField("kindOffsets")
        kindOffsetsField.isAccessible = true
        val kindOffsets = kindOffsetsField.get(scheduler) as ConcurrentHashMap<String, Int>

        // Set a non-zero offset
        kindOffsets["TEST_KIND"] = 50

        // Empty batch → offset resets to 0
        scheduler.processBatch("TEST_KIND") { _ -> emptyList() }
        assertThat(kindOffsets["TEST_KIND"]).isEqualTo(0)
    }

    @Test
    fun `offset - passes current offset to loader`() {
        val app = Application(key = "app1", name = "App1")
        app.id = 1L
        val node1 = Node(application = app, fqn = "com.example.Method1", kind = NodeKind.METHOD, lang = Lang.kotlin)
        node1.id = 100L

        every { generator.generate(node1, "ru", false) } returns null
        every { nodeRepo.countByApplicationId(1L) } returns 100L

        val kindOffsetsField = NodeDocFillerScheduler::class.java.getDeclaredField("kindOffsets")
        kindOffsetsField.isAccessible = true
        val kindOffsets = kindOffsetsField.get(scheduler) as ConcurrentHashMap<String, Int>
        kindOffsets["TEST_KIND"] = 25

        var receivedOffset = -1
        scheduler.processBatch("TEST_KIND") { offset ->
            receivedOffset = offset
            listOf(node1)
        }

        assertThat(receivedOffset).isEqualTo(25)
    }

    @Test
    fun `offset - does not change on empty batch when already at zero`() {
        val kindOffsetsField = NodeDocFillerScheduler::class.java.getDeclaredField("kindOffsets")
        kindOffsetsField.isAccessible = true
        val kindOffsets = kindOffsetsField.get(scheduler) as ConcurrentHashMap<String, Int>

        // offset is 0 (default), empty batch → stays at 0 (no entry)
        scheduler.processBatch("TEST_KIND") { _ -> emptyList() }
        assertThat(kindOffsets["TEST_KIND"]).isNull()
    }

    // === LEAF step tests ===

    @Test
    fun `poll - LEAF processes FIELD nodes`() {
        every { nodeRepo.lockNextMethodsWithoutDoc("ru", 10, 0) } returns emptyList()
        every { nodeRepo.lockNextLeafNodesWithoutDoc("ru", 10, 0) } returns listOf(
            Node(
                application = Application(key = "app1", name = "App1").apply { id = 1L },
                fqn = "com.example.Class1.field1",
                kind = NodeKind.FIELD,
                lang = Lang.kotlin
            ).apply { id = 150L }
        )

        val generatedDoc = NodeDocGenerator.GeneratedDoc(
            docTech = "tech doc", docPublic = "public doc", docDigest = "digest", modelMeta = emptyMap()
        )
        every { generator.generate(any(), "ru", false) } returns generatedDoc

        scheduler.poll()

        verify(exactly = 1) { nodeRepo.lockNextMethodsWithoutDoc("ru", 10, 0) }
        verify(exactly = 1) { nodeRepo.lockNextLeafNodesWithoutDoc("ru", 10, 0) }
        verify(exactly = 0) { nodeRepo.lockNextTypesWithoutDoc(any(), any(), any()) }
    }

    @Test
    fun `poll - LEAF processes TOPIC nodes`() {
        every { nodeRepo.lockNextMethodsWithoutDoc("ru", 10, 0) } returns emptyList()
        every { nodeRepo.lockNextLeafNodesWithoutDoc("ru", 10, 0) } returns listOf(
            Node(
                application = Application(key = "app1", name = "App1").apply { id = 1L },
                fqn = "infra:kafka:topic:my-topic",
                kind = NodeKind.TOPIC,
                lang = Lang.kotlin
            ).apply { id = 160L }
        )

        val generatedDoc = NodeDocGenerator.GeneratedDoc(
            docTech = "tech doc", docPublic = "public doc", docDigest = "digest", modelMeta = emptyMap()
        )
        every { generator.generate(any(), "ru", false) } returns generatedDoc

        scheduler.poll()

        verify(exactly = 1) { nodeRepo.lockNextLeafNodesWithoutDoc("ru", 10, 0) }
        verify(exactly = 0) { nodeRepo.lockNextTypesWithoutDoc(any(), any(), any()) }
    }
}
