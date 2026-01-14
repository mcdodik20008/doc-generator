package com.bftcom.docgenerator.graph.impl.node.builder.hashing

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CodeHasherImplTest {
    private val hasher = CodeHasherImpl()

    @Test
    fun `computeHash - null и blank возвращают null`() {
        assertThat(hasher.computeHash(null)).isNull()
        assertThat(hasher.computeHash("")).isNull()
        assertThat(hasher.computeHash("   ")).isNull()
    }

    @Test
    fun `computeHash - возвращает sha256 в hex`() {
        // sha256("abc")
        val expected = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
        assertThat(hasher.computeHash("abc")).isEqualTo(expected)
    }
}

