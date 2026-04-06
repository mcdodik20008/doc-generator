package com.bftcom.docgenerator.rag.impl.steps

import com.bftcom.docgenerator.rag.api.ProcessingStepType
import com.bftcom.docgenerator.rag.api.QueryMetadataKeys
import com.bftcom.docgenerator.rag.api.QueryProcessingContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class StacktraceParsingStepTest {
    private val step = StacktraceParsingStep()

    @Test
    fun `parses simple stacktrace`() {
        val query =
            """
            java.lang.NullPointerException: Cannot invoke method on null
                at com.example.service.UserService.getUser(UserService.kt:42)
                at com.example.controller.UserController.handleRequest(UserController.kt:18)
                at org.springframework.web.servlet.FrameworkServlet.service(FrameworkServlet.java:100)
            """.trimIndent()

        val context = makeContext(query)
        val result = step.execute(context)

        assertThat(result.transitionKey).isEqualTo("SUCCESS")

        val frames =
            result.context
                .getMetadata<List<*>>(QueryMetadataKeys.STACKTRACE_FRAMES)!!
                .filterIsInstance<StackFrame>()
        assertThat(frames).hasSize(3)
        assertThat(frames[0].className).isEqualTo("UserService")
        assertThat(frames[0].methodName).isEqualTo("getUser")
        assertThat(frames[0].lineNumber).isEqualTo(42)

        val appFrames =
            result.context
                .getMetadata<List<*>>(QueryMetadataKeys.STACKTRACE_APP_FRAMES)!!
                .filterIsInstance<StackFrame>()
        assertThat(appFrames).hasSize(2) // excludes Spring framework frame

        val exType = result.context.getMetadata<String>(QueryMetadataKeys.STACKTRACE_EXCEPTION_TYPE)
        assertThat(exType).isEqualTo("java.lang.NullPointerException")

        val exMsg = result.context.getMetadata<String>(QueryMetadataKeys.STACKTRACE_EXCEPTION_MESSAGE)
        assertThat(exMsg).isEqualTo("Cannot invoke method on null")
    }

    @Test
    fun `parses Caused-by chains`() {
        val query =
            """
            org.springframework.web.util.NestedServletException: Request processing failed
                at org.springframework.web.servlet.FrameworkServlet.service(FrameworkServlet.java:100)
            Caused by: java.lang.RuntimeException: User not found
                at com.example.service.UserService.getUser(UserService.kt:42)
            Caused by: java.lang.IllegalArgumentException: ID must be positive
                at com.example.util.Validator.checkId(Validator.kt:10)
                at com.example.service.UserService.getUser(UserService.kt:40)
            """.trimIndent()

        val context = makeContext(query)
        val result = step.execute(context)

        assertThat(result.transitionKey).isEqualTo("SUCCESS")

        // Root cause exception = deepest Caused by
        val exType = result.context.getMetadata<String>(QueryMetadataKeys.STACKTRACE_EXCEPTION_TYPE)
        assertThat(exType).isEqualTo("java.lang.IllegalArgumentException")

        // Root cause frame = first app frame in deepest Caused by
        val rootFrame = result.context.getMetadata<StackFrame>(QueryMetadataKeys.STACKTRACE_ROOT_CAUSE_FRAME)
        assertThat(rootFrame).isNotNull
        assertThat(rootFrame!!.className).isEqualTo("Validator")
        assertThat(rootFrame.methodName).isEqualTo("checkId")
    }

    @Test
    fun `handles mixed text and stacktrace`() {
        val query =
            """
            Привет, у меня ошибка при запуске:
            java.lang.NullPointerException: null
                at com.example.service.OrderService.process(OrderService.kt:55)
                at com.example.controller.OrderController.create(OrderController.kt:23)
            Помогите разобраться.
            """.trimIndent()

        val context = makeContext(query)
        val result = step.execute(context)

        assertThat(result.transitionKey).isEqualTo("SUCCESS")

        val appFrames =
            result.context
                .getMetadata<List<*>>(QueryMetadataKeys.STACKTRACE_APP_FRAMES)!!
                .filterIsInstance<StackFrame>()
        assertThat(appFrames).hasSize(2)
    }

    @Test
    fun `returns PARSE_FAILED for no stacktrace`() {
        val query = "Как работает авторизация в проекте?"

        val context = makeContext(query)
        val result = step.execute(context)

        assertThat(result.transitionKey).isEqualTo("PARSE_FAILED")
    }

    @Test
    fun `filters library frames correctly`() {
        val frames =
            step.parseFrames(
                """
                at com.example.MyService.doWork(MyService.kt:10)
                at org.springframework.beans.factory.BeanFactory.create(BeanFactory.java:200)
                at java.lang.Thread.run(Thread.java:829)
                at com.example.MyController.handle(MyController.kt:5)
                """.trimIndent(),
            )

        val appFrames =
            frames.filter { frame ->
                !StacktraceParsingStep.LIBRARY_PREFIXES.any { frame.fullClassName.startsWith(it) }
            }

        assertThat(appFrames).hasSize(2)
        assertThat(appFrames.map { it.className }).containsExactly("MyService", "MyController")
    }

    @Test
    fun `parseExceptionInfo extracts from simple exception`() {
        val (type, msg) = step.parseExceptionInfo("java.lang.NullPointerException: Value is null")
        assertThat(type).isEqualTo("java.lang.NullPointerException")
        assertThat(msg).isEqualTo("Value is null")
    }

    private fun makeContext(query: String): QueryProcessingContext =
        QueryProcessingContext(
            originalQuery = query,
            currentQuery = query,
            sessionId = "test-session",
        )
}
