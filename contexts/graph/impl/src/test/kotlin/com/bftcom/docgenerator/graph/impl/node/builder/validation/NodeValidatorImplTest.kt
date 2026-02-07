package com.bftcom.docgenerator.graph.impl.node.builder.validation

import com.bftcom.docgenerator.domain.application.Application
import com.bftcom.docgenerator.domain.enums.Lang
import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.domain.node.Node
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class NodeValidatorImplTest {
    private val validator = NodeValidatorImpl()
    private val app = Application(id = 1L, key = "app", name = "App")

    @Test
    fun `validate - валидные данные не кидают исключение`() {
        val parent = node(app, "com.example.Parent")
        assertThatCode {
            validator.validate(
                fqn = "com.example.Child",
                span = 1..10,
                parent = parent,
                sourceCode = "class Child",
                applicationId = 1L,
            )
        }.doesNotThrowAnyException()
    }

    @Test
    fun `validate - пустой fqn`() {
        assertThatThrownBy {
            validator.validate(
                fqn = "   ",
                span = null,
                parent = null,
                sourceCode = null,
                applicationId = 1L,
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `validate - некорректный формат fqn`() {
        assertThatThrownBy {
            validator.validate(
                fqn = "1bad.fqn",
                span = null,
                parent = null,
                sourceCode = null,
                applicationId = 1L,
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `validate - некорректный span`() {
        assertThatThrownBy {
            validator.validate(
                fqn = "com.example.Foo",
                span = (-1)..1,
                parent = null,
                sourceCode = null,
                applicationId = 1L,
            )
        }.isInstanceOf(IllegalArgumentException::class.java)

        assertThatThrownBy {
            validator.validate(
                fqn = "com.example.Foo",
                span = 10..9,
                parent = null,
                sourceCode = null,
                applicationId = 1L,
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `validate - parent должен быть из того же application`() {
        val otherApp = Application(id = 2L, key = "app2", name = "App2")
        val parent = node(otherApp, "com.example.Parent")

        assertThatThrownBy {
            validator.validate(
                fqn = "com.example.Child",
                span = null,
                parent = parent,
                sourceCode = null,
                applicationId = 1L,
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `validate - защита от самоссылки`() {
        val parent = node(app, "com.example.Same")
        assertThatThrownBy {
            validator.validate(
                fqn = "com.example.Same",
                span = null,
                parent = parent,
                sourceCode = null,
                applicationId = 1L,
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `validate - FQN слишком длинный`() {
        val longFqn = "a".repeat(1001)
        assertThatThrownBy {
            validator.validate(
                fqn = longFqn,
                span = null,
                parent = null,
                sourceCode = null,
                applicationId = 1L,
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("too long")
    }

    @Test
    fun `validate - FQN максимальной длины проходит`() {
        val maxFqn = "a".repeat(1000)
        assertThatCode {
            validator.validate(
                fqn = maxFqn,
                span = null,
                parent = null,
                sourceCode = null,
                applicationId = 1L,
            )
        }.doesNotThrowAnyException()
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = [
        "com.example.ValidClass",
        "com.example.Valid_Class",
        "com.example.Valid123",
        "_private.Class",
        "a.b.c",
        "ValidClass",
        "com.example.Class.method(Array)",
        "com.example.method(Array)",
        "com.bftcom.rr.uds.main(Array)",
        "com.example.Class.method(Type1,Type2)",
        "method(Type1)",
        "com.example.Class.method(Type1, Type2)",
        "com.example.Class.method()",
        "method()",
    ])
    fun `validate - валидные форматы FQN`(fqn: String) {
        assertThatCode {
            validator.validate(
                fqn = fqn,
                span = null,
                parent = null,
                sourceCode = null,
                applicationId = 1L,
            )
        }.doesNotThrowAnyException()
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = [
        "1invalid.fqn",
        "invalid-fqn",
        "invalid.fqn.with-dash",
        "invalid.fqn.with space",
        "invalid.fqn.with@symbol",
        "",
        "   ",
        "invalid.fqn.with space(Param)",
        "invalid.fqn.with@symbol(Param)",
    ])
    fun `validate - невалидные форматы FQN`(fqn: String) {
        assertThatThrownBy {
            validator.validate(
                fqn = fqn,
                span = null,
                parent = null,
                sourceCode = null,
                applicationId = 1L,
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `validate - null span проходит`() {
        assertThatCode {
            validator.validate(
                fqn = "com.example.Foo",
                span = null,
                parent = null,
                sourceCode = null,
                applicationId = 1L,
            )
        }.doesNotThrowAnyException()
    }

    @Test
    fun `validate - валидный span проходит`() {
        assertThatCode {
            validator.validate(
                fqn = "com.example.Foo",
                span = 0..10,
                parent = null,
                sourceCode = null,
                applicationId = 1L,
            )
        }.doesNotThrowAnyException()

        assertThatCode {
            validator.validate(
                fqn = "com.example.Foo",
                span = 5..5,
                parent = null,
                sourceCode = null,
                applicationId = 1L,
            )
        }.doesNotThrowAnyException()
    }

    @Test
    fun `validate - null parent проходит`() {
        assertThatCode {
            validator.validate(
                fqn = "com.example.Foo",
                span = null,
                parent = null,
                sourceCode = null,
                applicationId = 1L,
            )
        }.doesNotThrowAnyException()
    }

    @Test
    fun `validate - валидный parent проходит`() {
        val parent = node(app, "com.example.Parent")
        assertThatCode {
            validator.validate(
                fqn = "com.example.Child",
                span = null,
                parent = parent,
                sourceCode = null,
                applicationId = 1L,
            )
        }.doesNotThrowAnyException()
    }

    @Test
    fun `validate - null sourceCode проходит`() {
        assertThatCode {
            validator.validate(
                fqn = "com.example.Foo",
                span = null,
                parent = null,
                sourceCode = null,
                applicationId = 1L,
            )
        }.doesNotThrowAnyException()
    }

    @Test
    fun `validate - непустой sourceCode проходит`() {
        assertThatCode {
            validator.validate(
                fqn = "com.example.Foo",
                span = null,
                parent = null,
                sourceCode = "class Foo {}",
                applicationId = 1L,
            )
        }.doesNotThrowAnyException()
    }

    private fun node(application: Application, fqn: String): Node =
        Node(
            id = null,
            application = application,
            fqn = fqn,
            name = fqn.substringAfterLast('.'),
            packageName = fqn.substringBeforeLast('.', missingDelimiterValue = ""),
            kind = NodeKind.CLASS,
            lang = Lang.kotlin,
        )
}

