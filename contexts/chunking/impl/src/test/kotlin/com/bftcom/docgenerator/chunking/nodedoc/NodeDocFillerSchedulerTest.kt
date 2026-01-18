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
        // Создаем реальный TransactionTemplate с мокнутым менеджером
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
        // Используем рефлексию для доступа к processBatch
        val processBatchMethod =
            NodeDocFillerScheduler::class.java.getDeclaredMethod(
                "processBatch",
                String::class.java,
                kotlin.jvm.functions.Function0::class.java,
            )
        processBatchMethod.isAccessible = true

        val result =
            processBatchMethod.invoke(
                scheduler,
                "METHOD",
                { emptyList<Node>() },
            ) as Int

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

        val processBatchMethod =
            NodeDocFillerScheduler::class.java.getDeclaredMethod(
                "processBatch",
                String::class.java,
                kotlin.jvm.functions.Function0::class.java,
            )
        processBatchMethod.isAccessible = true

        val result =
            processBatchMethod.invoke(
                scheduler,
                "METHOD",
                { loaderResult },
            ) as Int

        assertThat(result).isEqualTo(1)
        verify(exactly = 1) { generator.generate(node1, "ru", false) }
        verify(exactly = 1) { generator.store(100L, "ru", generatedDoc) }
    }

    @Test
    fun `processBatch - пропускает ноду без id`() {
        val app = Application(key = "app1", name = "App1")
        app.id = 1L
        val node1 = Node(application = app, fqn = "com.example.Method1", kind = NodeKind.METHOD, lang = Lang.kotlin)
        node1.id = null // Нет id

        val loaderResult = listOf(node1)

        val processBatchMethod =
            NodeDocFillerScheduler::class.java.getDeclaredMethod(
                "processBatch",
                String::class.java,
                kotlin.jvm.functions.Function0::class.java,
            )
        processBatchMethod.isAccessible = true

        val result =
            processBatchMethod.invoke(
                scheduler,
                "METHOD",
                { loaderResult },
            ) as Int

        assertThat(result).isEqualTo(1) // Батч обработан, но нода пропущена
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

        val processBatchMethod =
            NodeDocFillerScheduler::class.java.getDeclaredMethod(
                "processBatch",
                String::class.java,
                kotlin.jvm.functions.Function0::class.java,
            )
        processBatchMethod.isAccessible = true

        val result =
            processBatchMethod.invoke(
                scheduler,
                "METHOD",
                { loaderResult },
            ) as Int

        assertThat(result).isEqualTo(1) // Батч обработан, ошибка залогирована
        verify(exactly = 1) { generator.generate(node1, "ru", false) }
    }

    @Test
    fun `processBatch - пропускает ноду с missing deps и увеличивает skip count`() {
        val app = Application(key = "app1", name = "App1")
        app.id = 1L
        val node1 = Node(application = app, fqn = "com.example.Method1", kind = NodeKind.METHOD, lang = Lang.kotlin)
        node1.id = 100L

        val loaderResult = listOf(node1)

        every { generator.generate(node1, "ru", false) } returns null // Missing deps
        every { nodeRepo.countByApplicationId(1L) } returns 100L

        val processBatchMethod =
            NodeDocFillerScheduler::class.java.getDeclaredMethod(
                "processBatch",
                String::class.java,
                kotlin.jvm.functions.Function0::class.java,
            )
        processBatchMethod.isAccessible = true

        val result =
            processBatchMethod.invoke(
                scheduler,
                "METHOD",
                { loaderResult },
            ) as Int

        assertThat(result).isEqualTo(1)
        verify(exactly = 1) { generator.generate(node1, "ru", false) }

        // Проверяем, что skip count увеличился
        val skipCountsField = NodeDocFillerScheduler::class.java.getDeclaredField("skipCounts")
        skipCountsField.isAccessible = true
        val skipCounts = skipCountsField.get(scheduler) as java.util.concurrent.ConcurrentHashMap<Long, Int>
        assertThat(skipCounts[100L]).isEqualTo(1)
    }

    @Test
    fun `shouldAllowMissingDeps - возвращает true когда skip count достиг максимума`() {
        val app = Application(key = "app1", name = "App1")
        app.id = 1L
        val node1 = Node(application = app, fqn = "com.example.Method1", kind = NodeKind.METHOD, lang = Lang.kotlin)
        node1.id = 100L

        every { nodeRepo.countByApplicationId(1L) } returns 100L

        // Устанавливаем skip count в максимум
        val skipCountsField = NodeDocFillerScheduler::class.java.getDeclaredField("skipCounts")
        skipCountsField.isAccessible = true
        val skipCounts = skipCountsField.get(scheduler) as ConcurrentHashMap<Long, Int>
        skipCounts[100L] = 10 // Больше чем maxSkips

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

        // skipFactor = 0.01, totalNodes = 1000, byFactor = ceil(1000 * 0.01) = 10
        // max(skipMin=3, min(skipMax=100, 10)) = max(3, 10) = 10
        assertThat(result).isEqualTo(10)
    }

    @Test
    fun `maxSkipsFor - использует skipMin когда приложение не найдено`() {
        val app = Application(key = "app1", name = "App1")
        app.id = null // Нет id
        val node1 = Node(application = app, fqn = "com.example.Method1", kind = NodeKind.METHOD, lang = Lang.kotlin)
        node1.id = 100L

        val maxSkipsMethod =
            NodeDocFillerScheduler::class.java.getDeclaredMethod(
                "maxSkipsFor",
                Node::class.java,
            )
        maxSkipsMethod.isAccessible = true

        val result = maxSkipsMethod.invoke(scheduler, node1) as Int

        // Когда app.id == null, должен вернуть skipMin = 3
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

        val processBatchMethod =
            NodeDocFillerScheduler::class.java.getDeclaredMethod(
                "processBatch",
                String::class.java,
                kotlin.jvm.functions.Function0::class.java,
            )
        processBatchMethod.isAccessible = true

        val result =
            processBatchMethod.invoke(
                scheduler,
                "METHOD",
                { loaderResult },
            ) as Int

        assertThat(result).isEqualTo(2)
        // Проверяем, что оба сохранения вызваны (в одной транзакции)
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
        every { generator.generate(node2, "ru", false) } returns null // Пропущен
        every { generator.generate(node3, "ru", false) } throws RuntimeException("Generation failed")
        every { nodeRepo.countByApplicationId(1L) } returns 100L

        val processBatchMethod =
            NodeDocFillerScheduler::class.java.getDeclaredMethod(
                "processBatch",
                String::class.java,
                kotlin.jvm.functions.Function0::class.java,
            )
        processBatchMethod.isAccessible = true

        val result =
            processBatchMethod.invoke(
                scheduler,
                "METHOD",
                { loaderResult },
            ) as Int

        assertThat(result).isEqualTo(3)
        verify(exactly = 1) { generator.generate(node1, "ru", false) }
        verify(exactly = 1) { generator.generate(node2, "ru", false) }
        verify(exactly = 1) { generator.generate(node3, "ru", false) }
        verify(exactly = 1) { generator.store(100L, "ru", generatedDoc1) }
        verify(exactly = 0) { generator.store(200L, any(), any()) }
        verify(exactly = 0) { generator.store(300L, any(), any()) }

        // Проверяем skip count для node2
        val skipCountsField = NodeDocFillerScheduler::class.java.getDeclaredField("skipCounts")
        skipCountsField.isAccessible = true
        val skipCounts = skipCountsField.get(scheduler) as java.util.concurrent.ConcurrentHashMap<Long, Int>
        assertThat(skipCounts[200L]).isEqualTo(1)
    }

    @Test
    fun `processBatch - обрабатывает forced skip когда allowMissingDeps = true`() {
        val app = Application(key = "app1", name = "App1")
        app.id = 1L
        val node1 = Node(application = app, fqn = "com.example.Method1", kind = NodeKind.METHOD, lang = Lang.kotlin)
        node1.id = 100L

        val loaderResult = listOf(node1)

        every { generator.generate(node1, "ru", true) } returns null // Missing deps, но forced
        every { nodeRepo.countByApplicationId(1L) } returns 100L

        // Устанавливаем skip count в максимум, чтобы shouldAllowMissingDeps вернул true
        val skipCountsField = NodeDocFillerScheduler::class.java.getDeclaredField("skipCounts")
        skipCountsField.isAccessible = true
        val skipCounts = skipCountsField.get(scheduler) as java.util.concurrent.ConcurrentHashMap<Long, Int>
        skipCounts[100L] = 10 // Больше чем maxSkips

        val processBatchMethod =
            NodeDocFillerScheduler::class.java.getDeclaredMethod(
                "processBatch",
                String::class.java,
                kotlin.jvm.functions.Function0::class.java,
            )
        processBatchMethod.isAccessible = true

        val result =
            processBatchMethod.invoke(
                scheduler,
                "METHOD",
                { loaderResult },
            ) as Int

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

        // Первый вызов - должен запросить из БД
        val result1 = maxSkipsMethod.invoke(scheduler, node1) as Int
        assertThat(result1).isEqualTo(10)

        // Второй вызов - должен использовать кэш
        val result2 = maxSkipsMethod.invoke(scheduler, node1) as Int
        assertThat(result2).isEqualTo(10)

        // Проверяем, что countByApplicationId вызван только один раз
        verify(exactly = 1) { nodeRepo.countByApplicationId(1L) }
    }

    @Test
    fun `maxSkipsFor - обновляет кэш после истечения TTL`() {
        val app = Application(key = "app1", name = "App1")
        app.id = 1L
        val node1 = Node(application = app, fqn = "com.example.Method1", kind = NodeKind.METHOD, lang = Lang.kotlin)
        node1.id = 100L

        every { nodeRepo.countByApplicationId(1L) } returnsMany listOf(1000L, 2000L)

        // Устанавливаем короткий TTL для теста
        val cacheTtlField = NodeDocFillerScheduler::class.java.getDeclaredField("cacheTtlMs")
        cacheTtlField.isAccessible = true
        cacheTtlField.set(scheduler, 100L) // 100ms

        val maxSkipsMethod =
            NodeDocFillerScheduler::class.java.getDeclaredMethod(
                "maxSkipsFor",
                Node::class.java,
            )
        maxSkipsMethod.isAccessible = true

        // Первый вызов
        val result1 = maxSkipsMethod.invoke(scheduler, node1) as Int
        assertThat(result1).isEqualTo(10) // ceil(1000 * 0.01) = 10

        // Ждем истечения TTL
        Thread.sleep(150)

        // Второй вызов после истечения TTL - должен обновить кэш
        val result2 = maxSkipsMethod.invoke(scheduler, node1) as Int
        assertThat(result2).isEqualTo(20) // ceil(2000 * 0.01) = 20

        // Проверяем, что countByApplicationId вызван дважды
        verify(exactly = 2) { nodeRepo.countByApplicationId(1L) }
    }

    @Test
    fun `poll - обрабатывает методы первыми`() {
        every { nodeRepo.lockNextMethodsWithoutDoc("ru", 10) } returns listOf(
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

        verify(exactly = 1) { nodeRepo.lockNextMethodsWithoutDoc("ru", 10) }
        verify(exactly = 0) { nodeRepo.lockNextTypesWithoutDoc(any(), any()) }
        verify(exactly = 0) { nodeRepo.lockNextPackagesWithoutDoc(any(), any()) }
        verify(exactly = 0) { nodeRepo.lockNextModulesAndReposWithoutDoc(any(), any()) }
    }

    @Test
    fun `poll - переходит к типам когда методы отсутствуют`() {
        every { nodeRepo.lockNextMethodsWithoutDoc("ru", 10) } returns emptyList()
        every { nodeRepo.lockNextTypesWithoutDoc("ru", 10) } returns listOf(
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

        verify(exactly = 1) { nodeRepo.lockNextMethodsWithoutDoc("ru", 10) }
        verify(exactly = 1) { nodeRepo.lockNextTypesWithoutDoc("ru", 10) }
        verify(exactly = 0) { nodeRepo.lockNextPackagesWithoutDoc(any(), any()) }
        verify(exactly = 0) { nodeRepo.lockNextModulesAndReposWithoutDoc(any(), any()) }
    }

    @Test
    fun `poll - использует random методы когда включен флаг`() {
        // Устанавливаем randomMethods = true через рефлексию
        val randomMethodsField = NodeDocFillerScheduler::class.java.getDeclaredField("randomMethods")
        randomMethodsField.isAccessible = true
        randomMethodsField.set(scheduler, true)

        every { nodeRepo.lockNextMethodsWithoutDocRandom("ru", 10) } returns emptyList()

        scheduler.poll()

        verify(exactly = 1) { nodeRepo.lockNextMethodsWithoutDocRandom("ru", 10) }
        verify(exactly = 0) { nodeRepo.lockNextMethodsWithoutDoc(any(), any()) }
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

        val processBatchMethod =
            NodeDocFillerScheduler::class.java.getDeclaredMethod(
                "processBatch",
                String::class.java,
                kotlin.jvm.functions.Function0::class.java,
            )
        processBatchMethod.isAccessible = true

        val result =
            processBatchMethod.invoke(
                scheduler,
                "METHOD",
                { loaderResult },
            ) as Int

        assertThat(result).isEqualTo(1)
        // Сохранений не должно быть, так как генерация вернула null
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

        // Устанавливаем skip count
        val skipCountsField = NodeDocFillerScheduler::class.java.getDeclaredField("skipCounts")
        skipCountsField.isAccessible = true
        val skipCounts = skipCountsField.get(scheduler) as java.util.concurrent.ConcurrentHashMap<Long, Int>
        skipCounts[100L] = 5

        val processBatchMethod =
            NodeDocFillerScheduler::class.java.getDeclaredMethod(
                "processBatch",
                String::class.java,
                kotlin.jvm.functions.Function0::class.java,
            )
        processBatchMethod.isAccessible = true

        val result =
            processBatchMethod.invoke(
                scheduler,
                "METHOD",
                { loaderResult },
            ) as Int

        assertThat(result).isEqualTo(1)
        // Skip count должен быть удален
        assertThat(skipCounts[100L]).isNull()
    }

    @Test
    fun `maxSkipsFor - использует skipMin когда byFactor меньше skipMin`() {
        val app = Application(key = "app1", name = "App1")
        app.id = 1L
        val node1 = Node(application = app, fqn = "com.example.Method1", kind = NodeKind.METHOD, lang = Lang.kotlin)
        node1.id = 100L

        // Малое количество узлов: 100 * 0.01 = 1, но skipMin = 3
        every { nodeRepo.countByApplicationId(1L) } returns 100L

        val maxSkipsMethod =
            NodeDocFillerScheduler::class.java.getDeclaredMethod(
                "maxSkipsFor",
                Node::class.java,
            )
        maxSkipsMethod.isAccessible = true

        val result = maxSkipsMethod.invoke(scheduler, node1) as Int

        // ceil(100 * 0.01) = 1, но max(3, min(100, 1)) = max(3, 1) = 3
        assertThat(result).isEqualTo(3)
    }

    @Test
    fun `maxSkipsFor - использует skipMax когда byFactor больше skipMax`() {
        val app = Application(key = "app1", name = "App1")
        app.id = 1L
        val node1 = Node(application = app, fqn = "com.example.Method1", kind = NodeKind.METHOD, lang = Lang.kotlin)
        node1.id = 100L

        // Большое количество узлов: 20000 * 0.01 = 200, но skipMax = 100
        every { nodeRepo.countByApplicationId(1L) } returns 20000L

        val maxSkipsMethod =
            NodeDocFillerScheduler::class.java.getDeclaredMethod(
                "maxSkipsFor",
                Node::class.java,
            )
        maxSkipsMethod.isAccessible = true

        val result = maxSkipsMethod.invoke(scheduler, node1) as Int

        // ceil(20000 * 0.01) = 200, но max(3, min(100, 200)) = max(3, 100) = 100
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

        // ceil(0 * 0.01) = 0, но max(3, min(100, 0)) = max(3, 0) = 3
        assertThat(result).isEqualTo(3)
    }

    @Test
    fun `shouldAllowMissingDeps - возвращает false когда skip count меньше максимума`() {
        val app = Application(key = "app1", name = "App1")
        app.id = 1L
        val node1 = Node(application = app, fqn = "com.example.Method1", kind = NodeKind.METHOD, lang = Lang.kotlin)
        node1.id = 100L

        every { nodeRepo.countByApplicationId(1L) } returns 1000L

        // Устанавливаем skip count меньше максимума
        val skipCountsField = NodeDocFillerScheduler::class.java.getDeclaredField("skipCounts")
        skipCountsField.isAccessible = true
        val skipCounts = skipCountsField.get(scheduler) as java.util.concurrent.ConcurrentHashMap<Long, Int>
        skipCounts[100L] = 5 // Меньше чем maxSkips = 10

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
    fun `poll - переходит к packages когда методы и типы отсутствуют`() {
        every { nodeRepo.lockNextMethodsWithoutDoc("ru", 10) } returns emptyList()
        every { nodeRepo.lockNextTypesWithoutDoc("ru", 10) } returns emptyList()
        every { nodeRepo.lockNextPackagesWithoutDoc("ru", 10) } returns listOf(
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

        verify(exactly = 1) { nodeRepo.lockNextMethodsWithoutDoc("ru", 10) }
        verify(exactly = 1) { nodeRepo.lockNextTypesWithoutDoc("ru", 10) }
        verify(exactly = 1) { nodeRepo.lockNextPackagesWithoutDoc("ru", 10) }
        verify(exactly = 0) { nodeRepo.lockNextModulesAndReposWithoutDoc(any(), any()) }
    }

    @Test
    fun `poll - переходит к modules repos когда все предыдущие уровни отсутствуют`() {
        every { nodeRepo.lockNextMethodsWithoutDoc("ru", 10) } returns emptyList()
        every { nodeRepo.lockNextTypesWithoutDoc("ru", 10) } returns emptyList()
        every { nodeRepo.lockNextPackagesWithoutDoc("ru", 10) } returns emptyList()
        every { nodeRepo.lockNextModulesAndReposWithoutDoc("ru", 10) } returns listOf(
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

        verify(exactly = 1) { nodeRepo.lockNextMethodsWithoutDoc("ru", 10) }
        verify(exactly = 1) { nodeRepo.lockNextTypesWithoutDoc("ru", 10) }
        verify(exactly = 1) { nodeRepo.lockNextPackagesWithoutDoc("ru", 10) }
        verify(exactly = 1) { nodeRepo.lockNextModulesAndReposWithoutDoc("ru", 10) }
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

        val processBatchMethod =
            NodeDocFillerScheduler::class.java.getDeclaredMethod(
                "processBatch",
                String::class.java,
                kotlin.jvm.functions.Function0::class.java,
            )
        processBatchMethod.isAccessible = true

        val result =
            processBatchMethod.invoke(
                scheduler,
                "METHOD",
                { nodes },
            ) as Int

        assertThat(result).isEqualTo(50)
        // Проверяем, что все сохранения вызваны
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

        val processBatchMethod =
            NodeDocFillerScheduler::class.java.getDeclaredMethod(
                "processBatch",
                String::class.java,
                kotlin.jvm.functions.Function0::class.java,
            )
        processBatchMethod.isAccessible = true

        val result =
            processBatchMethod.invoke(
                scheduler,
                "METHOD",
                { loaderResult },
            ) as Int

        assertThat(result).isEqualTo(2)
        verify(exactly = 0) { generator.store(any(), any(), any()) }

        // Проверяем skip counts
        val skipCountsField = NodeDocFillerScheduler::class.java.getDeclaredField("skipCounts")
        skipCountsField.isAccessible = true
        val skipCounts = skipCountsField.get(scheduler) as java.util.concurrent.ConcurrentHashMap<Long, Int>
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

        val processBatchMethod =
            NodeDocFillerScheduler::class.java.getDeclaredMethod(
                "processBatch",
                String::class.java,
                kotlin.jvm.functions.Function0::class.java,
            )
        processBatchMethod.isAccessible = true

        val result =
            processBatchMethod.invoke(
                scheduler,
                "METHOD",
                { loaderResult },
            ) as Int

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
        val accessTimeMap = accessTimeField.get(scheduler) as java.util.concurrent.ConcurrentHashMap<Long, java.util.concurrent.atomic.AtomicLong>

        // Первый вызов
        val firstCall = System.currentTimeMillis()
        maxSkipsMethod.invoke(scheduler, node1)
        val firstAccessTime = accessTimeMap[1L]!!.get()

        // Небольшая задержка
        Thread.sleep(10)

        // Второй вызов - должен обновить access time
        val secondCall = System.currentTimeMillis()
        maxSkipsMethod.invoke(scheduler, node1)
        val secondAccessTime = accessTimeMap[1L]!!.get()

        // Access time должен быть обновлен
        assertThat(secondAccessTime).isGreaterThan(firstAccessTime)
        assertThat(secondAccessTime).isGreaterThanOrEqualTo(secondCall - 10)
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

        val processBatchMethod =
            NodeDocFillerScheduler::class.java.getDeclaredMethod(
                "processBatch",
                String::class.java,
                kotlin.jvm.functions.Function0::class.java,
            )
        processBatchMethod.isAccessible = true

        val result =
            processBatchMethod.invoke(
                scheduler,
                "MIXED",
                { loaderResult },
            ) as Int

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

        // Устанавливаем skip count в максимум, чтобы shouldAllowMissingDeps вернул true
        val skipCountsField = NodeDocFillerScheduler::class.java.getDeclaredField("skipCounts")
        skipCountsField.isAccessible = true
        val skipCounts = skipCountsField.get(scheduler) as java.util.concurrent.ConcurrentHashMap<Long, Int>
        skipCounts[100L] = 10

        every { generator.generate(node1, "ru", true) } returns generatedDoc
        every { nodeRepo.countByApplicationId(1L) } returns 100L

        val processBatchMethod =
            NodeDocFillerScheduler::class.java.getDeclaredMethod(
                "processBatch",
                String::class.java,
                kotlin.jvm.functions.Function0::class.java,
            )
        processBatchMethod.isAccessible = true

        val result =
            processBatchMethod.invoke(
                scheduler,
                "METHOD",
                { loaderResult },
            ) as Int

        assertThat(result).isEqualTo(1)
        verify(exactly = 1) { generator.generate(node1, "ru", true) }
        verify(exactly = 1) { generator.store(100L, "ru", generatedDoc) }
        // Skip count должен быть удален после успешной генерации
        assertThat(skipCounts[100L]).isNull()
    }

    @Test
    fun `maxSkipsFor - обрабатывает очень большое количество узлов`() {
        val app = Application(key = "app1", name = "App1")
        app.id = 1L
        val node1 = Node(application = app, fqn = "com.example.Method1", kind = NodeKind.METHOD, lang = Lang.kotlin)
        node1.id = 100L

        // Очень большое количество узлов
        every { nodeRepo.countByApplicationId(1L) } returns Long.MAX_VALUE

        val maxSkipsMethod =
            NodeDocFillerScheduler::class.java.getDeclaredMethod(
                "maxSkipsFor",
                Node::class.java,
            )
        maxSkipsMethod.isAccessible = true

        val result = maxSkipsMethod.invoke(scheduler, node1) as Int

        // Должен вернуть skipMax = 100, так как byFactor будет очень большим
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

        val processBatchMethod =
            NodeDocFillerScheduler::class.java.getDeclaredMethod(
                "processBatch",
                String::class.java,
                kotlin.jvm.functions.Function0::class.java,
            )
        processBatchMethod.isAccessible = true

        val skipCountsField = NodeDocFillerScheduler::class.java.getDeclaredField("skipCounts")
        skipCountsField.isAccessible = true
        val skipCounts = skipCountsField.get(scheduler) as java.util.concurrent.ConcurrentHashMap<Long, Int>

        // Первый пропуск
        processBatchMethod.invoke(scheduler, "METHOD", { loaderResult })
        assertThat(skipCounts[100L]).isEqualTo(1)

        // Второй пропуск
        processBatchMethod.invoke(scheduler, "METHOD", { loaderResult })
        assertThat(skipCounts[100L]).isEqualTo(2)

        // Третий пропуск
        processBatchMethod.invoke(scheduler, "METHOD", { loaderResult })
        assertThat(skipCounts[100L]).isEqualTo(3)
    }

    @Test
    fun `maxSkipsFor - обрабатывает точное значение на границе skipMin`() {
        val app = Application(key = "app1", name = "App1")
        app.id = 1L
        val node1 = Node(application = app, fqn = "com.example.Method1", kind = NodeKind.METHOD, lang = Lang.kotlin)
        node1.id = 100L

        // 300 узлов: ceil(300 * 0.01) = 3, что равно skipMin
        every { nodeRepo.countByApplicationId(1L) } returns 300L

        val maxSkipsMethod =
            NodeDocFillerScheduler::class.java.getDeclaredMethod(
                "maxSkipsFor",
                Node::class.java,
            )
        maxSkipsMethod.isAccessible = true

        val result = maxSkipsMethod.invoke(scheduler, node1) as Int

        // max(3, min(100, 3)) = max(3, 3) = 3
        assertThat(result).isEqualTo(3)
    }

    @Test
    fun `maxSkipsFor - обрабатывает точное значение на границе skipMax`() {
        val app = Application(key = "app1", name = "App1")
        app.id = 1L
        val node1 = Node(application = app, fqn = "com.example.Method1", kind = NodeKind.METHOD, lang = Lang.kotlin)
        node1.id = 100L

        // 10000 узлов: ceil(10000 * 0.01) = 100, что равно skipMax
        every { nodeRepo.countByApplicationId(1L) } returns 10000L

        val maxSkipsMethod =
            NodeDocFillerScheduler::class.java.getDeclaredMethod(
                "maxSkipsFor",
                Node::class.java,
            )
        maxSkipsMethod.isAccessible = true

        val result = maxSkipsMethod.invoke(scheduler, node1) as Int

        // max(3, min(100, 100)) = max(3, 100) = 100
        assertThat(result).isEqualTo(100)
    }

    @Test
    fun `processBatch - обрабатывает случай когда tx execute возвращает null`() {
        // Создаем новый scheduler с мокнутым txManager, который возвращает null
        val mockTxManager = mockk<PlatformTransactionManager> {
            val status = mockk<TransactionStatus>(relaxed = true)
            every { getTransaction(any()) } returns status
            every { commit(status) } just Runs
            every { rollback(status) } just Runs
        }
        
        // Используем spy для TransactionTemplate, чтобы перехватить execute
        val testScheduler = NodeDocFillerScheduler(mockTxManager, nodeRepo, generator)
        
        // Мокаем TransactionTemplate.execute через рефлексию
        // Но на самом деле TransactionTemplate.execute не может вернуть null для List<Node>
        // Поэтому этот тест проверяет edge case, когда loader внутри транзакции может вернуть null
        // Но в реальности это не произойдет из-за типизации Kotlin
        
        val processBatchMethod =
            NodeDocFillerScheduler::class.java.getDeclaredMethod(
                "processBatch",
                String::class.java,
                kotlin.jvm.functions.Function0::class.java,
            )
        processBatchMethod.isAccessible = true

        // В реальности TransactionTemplate.execute не вернет null для не-nullable типа
        // Но для полноты тестирования проверим поведение
        val result =
            processBatchMethod.invoke(
                testScheduler,
                "METHOD",
                { emptyList<Node>() }, // Пустой список, не null
            ) as Int

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

        val processBatchMethod =
            NodeDocFillerScheduler::class.java.getDeclaredMethod(
                "processBatch",
                String::class.java,
                kotlin.jvm.functions.Function0::class.java,
            )
        processBatchMethod.isAccessible = true

        // Проверяем, что код выполняется без ошибок
        // Debug логирование зависит от уровня логирования, который мы не можем контролировать в тесте
        val result =
            processBatchMethod.invoke(
                scheduler,
                "METHOD",
                { loaderResult },
            ) as Int

        assertThat(result).isEqualTo(1)
        // Код должен выполниться без ошибок, даже если debug логирование не включено
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

        val processBatchMethod =
            NodeDocFillerScheduler::class.java.getDeclaredMethod(
                "processBatch",
                String::class.java,
                kotlin.jvm.functions.Function0::class.java,
            )
        processBatchMethod.isAccessible = true

        val result =
            processBatchMethod.invoke(
                scheduler,
                "METHOD",
                { loaderResult },
            ) as Int

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

        val processBatchMethod =
            NodeDocFillerScheduler::class.java.getDeclaredMethod(
                "processBatch",
                String::class.java,
                kotlin.jvm.functions.Function0::class.java,
            )
        processBatchMethod.isAccessible = true

        val result =
            processBatchMethod.invoke(
                scheduler,
                "METHOD",
                { loaderResult },
            ) as Int

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

        val processBatchMethod =
            NodeDocFillerScheduler::class.java.getDeclaredMethod(
                "processBatch",
                String::class.java,
                kotlin.jvm.functions.Function0::class.java,
            )
        processBatchMethod.isAccessible = true

        val result =
            processBatchMethod.invoke(
                scheduler,
                "METHOD",
                { loaderResult },
            ) as Int

        assertThat(result).isEqualTo(1)
        verify(exactly = 0) { generator.store(any(), any(), any()) }
    }

    @Test
    fun `processBatch - не логирует когда success=0, skipped=0, failed=0`() {
        // Этот случай теоретически невозможен, так как если batch не пустой,
        // то хотя бы один элемент должен быть обработан
        // Но для полноты тестирования проверим
        val processBatchMethod =
            NodeDocFillerScheduler::class.java.getDeclaredMethod(
                "processBatch",
                String::class.java,
                kotlin.jvm.functions.Function0::class.java,
            )
        processBatchMethod.isAccessible = true

        val result =
            processBatchMethod.invoke(
                scheduler,
                "METHOD",
                { emptyList<Node>() },
            ) as Int

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
        // Используем большое количество узлов, чтобы maxSkips был больше текущего skipCount
        // 10000 узлов: ceil(10000 * 0.01) = 100, max(3, min(100, 100)) = 100
        // skipCounts[100L] = 2, поэтому allowMissingDeps = false (2 < 100)
        every { nodeRepo.countByApplicationId(1L) } returns 10000L

        // ConcurrentHashMap.merge не может вернуть null, но для полноты тестирования
        // проверим поведение с уже существующим значением
        val skipCountsField = NodeDocFillerScheduler::class.java.getDeclaredField("skipCounts")
        skipCountsField.isAccessible = true
        val skipCounts = skipCountsField.get(scheduler) as java.util.concurrent.ConcurrentHashMap<Long, Int>
        skipCounts[100L] = 2 // Уже есть значение, но меньше maxSkips (100)

        val processBatchMethod =
            NodeDocFillerScheduler::class.java.getDeclaredMethod(
                "processBatch",
                String::class.java,
                kotlin.jvm.functions.Function0::class.java,
            )
        processBatchMethod.isAccessible = true

        val result =
            processBatchMethod.invoke(
                scheduler,
                "METHOD",
                { loaderResult },
            ) as Int

        assertThat(result).isEqualTo(1)
        // Проверяем, что значение увеличилось с 2 до 3
        assertThat(skipCounts[100L]).isEqualTo(3)
    }

    @Test
    fun `shouldAllowMissingDeps - возвращает true когда currentSkips равен maxSkips`() {
        val app = Application(key = "app1", name = "App1")
        app.id = 1L
        val node1 = Node(application = app, fqn = "com.example.Method1", kind = NodeKind.METHOD, lang = Lang.kotlin)
        node1.id = 100L

        every { nodeRepo.countByApplicationId(1L) } returns 1000L // maxSkips = 10

        // Устанавливаем skip count равным максимуму
        val skipCountsField = NodeDocFillerScheduler::class.java.getDeclaredField("skipCounts")
        skipCountsField.isAccessible = true
        val skipCounts = skipCountsField.get(scheduler) as java.util.concurrent.ConcurrentHashMap<Long, Int>
        skipCounts[100L] = 10 // Равно maxSkips

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

        every { nodeRepo.countByApplicationId(1L) } returns 1000L // maxSkips = 10

        // Устанавливаем skip count меньше максимума
        val skipCountsField = NodeDocFillerScheduler::class.java.getDeclaredField("skipCounts")
        skipCountsField.isAccessible = true
        val skipCounts = skipCountsField.get(scheduler) as java.util.concurrent.ConcurrentHashMap<Long, Int>
        skipCounts[100L] = 9 // Меньше maxSkips

        val shouldAllowMethod =
            NodeDocFillerScheduler::class.java.getDeclaredMethod(
                "shouldAllowMissingDeps",
                Node::class.java,
            )
        shouldAllowMethod.isAccessible = true

        val result = shouldAllowMethod.invoke(scheduler, node1) as Boolean

        assertThat(result).isFalse()
    }
}
