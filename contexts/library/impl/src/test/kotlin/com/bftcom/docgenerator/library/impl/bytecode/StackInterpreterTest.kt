package com.bftcom.docgenerator.library.impl.bytecode

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.objectweb.asm.Type

class StackInterpreterTest {
    @Test
    fun `visitLdc - кладет строки и unknown`() {
        val si = StackInterpreter()
        si.visitLdc("x")
        assertThat(si.peekString()).isEqualTo("x")

        si.visitLdc(1)
        assertThat(si.size()).isEqualTo(2)
        assertThat(si.peekString()).isNull()

        si.visitLdc(Type.getType(String::class.java))
        assertThat(si.size()).isEqualTo(3)
    }

    @Test
    fun `StringBuilder append + toString`() {
        val si = StackInterpreter()
        si.visitNewStringBuilder()
        si.visitLdc("a")
        si.visitStringBuilderAppend("(Ljava/lang/String;)Ljava/lang/StringBuilder;")
        si.visitStringBuilderToString()
        assertThat(si.peekString()).isEqualTo("a")
    }

    @Test
    fun `String concat соединяет строки`() {
        val si = StackInterpreter()
        si.visitLdc("a")
        si.visitLdc("b")
        si.visitStringConcat()
        assertThat(si.popString()).isEqualTo("ab")
    }

    @Test
    fun `popStringArg - ищет последнюю строку в стеке`() {
        val si = StackInterpreter()
        si.visitLdc(1)
        si.visitLdc("url")
        si.visitLdc(2)
        assertThat(si.popStringArg()).isEqualTo("url")
    }

    @Test
    fun `clear - очищает стек`() {
        val si = StackInterpreter()
        repeat(3) { si.visitLdc("x") }
        si.clear()
        assertThat(si.size()).isEqualTo(0)
    }
}

