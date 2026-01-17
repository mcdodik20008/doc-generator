package com.bftcom.docgenerator.postprocessor.utils

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PpUtilTest {

    @Test
    fun `sha256Hex - вычисляет правильный хэш для пустой строки`() {
        val result = PpUtil.sha256Hex("")
        // SHA-256 от пустой строки
        val expected = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        assertThat(result).isEqualTo(expected)
    }

    @Test
    fun `sha256Hex - вычисляет правильный хэш для непустой строки`() {
        val result = PpUtil.sha256Hex("hello world")
        // SHA-256 от "hello world"
        val expected = "b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9"
        assertThat(result).isEqualTo(expected)
    }

    @Test
    fun `sha256Hex - вычисляет правильный хэш для кириллицы`() {
        val result = PpUtil.sha256Hex("Привет, мир!")
        assertThat(result).isNotEmpty
        assertThat(result).hasSize(64) // SHA-256 всегда 64 hex символа
    }

    @Test
    fun `sha256Hex - одинаковые строки дают одинаковый хэш`() {
        val text = "test string"
        val hash1 = PpUtil.sha256Hex(text)
        val hash2 = PpUtil.sha256Hex(text)
        assertThat(hash1).isEqualTo(hash2)
    }

    @Test
    fun `sha256Hex - разные строки дают разные хэши`() {
        val hash1 = PpUtil.sha256Hex("test1")
        val hash2 = PpUtil.sha256Hex("test2")
        assertThat(hash1).isNotEqualTo(hash2)
    }

    @Test
    fun `tokenCount - подсчитывает токены в простой строке`() {
        val result = PpUtil.tokenCount("hello world")
        assertThat(result).isEqualTo(2)
    }

    @Test
    fun `tokenCount - подсчитывает токены с пробелами`() {
        val result = PpUtil.tokenCount("  hello   world  test  ")
        assertThat(result).isEqualTo(3)
    }

    @Test
    fun `tokenCount - возвращает 0 для пустой строки`() {
        val result = PpUtil.tokenCount("")
        assertThat(result).isEqualTo(0)
    }

    @Test
    fun `tokenCount - подсчитывает токены с переносами строк`() {
        val result = PpUtil.tokenCount("hello\nworld\ntest")
        assertThat(result).isEqualTo(3)
    }

    @Test
    fun `tokenCount - подсчитывает токены с табуляцией`() {
        val result = PpUtil.tokenCount("hello\tworld\ttest")
        assertThat(result).isEqualTo(3)
    }

    @Test
    fun `tokenCount - подсчитывает токены с различными разделителями`() {
        val result = PpUtil.tokenCount("hello world, test! 123")
        assertThat(result).isEqualTo(4)
    }

    @Test
    fun `spanCharsRange - возвращает правильный формат для пустой строки`() {
        val result = PpUtil.spanCharsRange("")
        assertThat(result).isEqualTo("[0,0)")
    }

    @Test
    fun `spanCharsRange - возвращает правильный формат для непустой строки`() {
        val result = PpUtil.spanCharsRange("hello")
        assertThat(result).isEqualTo("[0,5)")
    }

    @Test
    fun `spanCharsRange - возвращает правильный формат для длинной строки`() {
        val text = "a".repeat(1000)
        val result = PpUtil.spanCharsRange(text)
        assertThat(result).isEqualTo("[0,1000)")
    }

    @Test
    fun `usesMarkdown - возвращает true для заголовков`() {
        assertThat(PpUtil.usesMarkdown("# Header")).isTrue()
        assertThat(PpUtil.usesMarkdown("## Header")).isTrue()
        assertThat(PpUtil.usesMarkdown("### Header")).isTrue()
        assertThat(PpUtil.usesMarkdown("#### Header")).isTrue()
        assertThat(PpUtil.usesMarkdown("##### Header")).isTrue()
        assertThat(PpUtil.usesMarkdown("###### Header")).isTrue()
    }

    @Test
    fun `usesMarkdown - возвращает true для ссылок`() {
        assertThat(PpUtil.usesMarkdown("[text](url)")).isTrue()
        assertThat(PpUtil.usesMarkdown("See [this link](https://example.com)")).isTrue()
    }

    @Test
    fun `usesMarkdown - возвращает true для кода`() {
        assertThat(PpUtil.usesMarkdown("`code`")).isTrue()
        assertThat(PpUtil.usesMarkdown("``code``")).isTrue()
        assertThat(PpUtil.usesMarkdown("```code```")).isTrue()
    }

    @Test
    fun `usesMarkdown - возвращает true для списков`() {
        assertThat(PpUtil.usesMarkdown("- item")).isTrue()
        assertThat(PpUtil.usesMarkdown("* item")).isTrue()
        assertThat(PpUtil.usesMarkdown("+ item")).isTrue()
    }

    @Test
    fun `usesMarkdown - возвращает true для жирного текста`() {
        assertThat(PpUtil.usesMarkdown("**bold**")).isTrue()
        assertThat(PpUtil.usesMarkdown("__bold__")).isTrue()
    }

    @Test
    fun `usesMarkdown - возвращает false для обычного текста`() {
        assertThat(PpUtil.usesMarkdown("plain text")).isFalse()
        assertThat(PpUtil.usesMarkdown("No markdown here")).isFalse()
    }

    @Test
    fun `usesMarkdown - возвращает false для пустой строки`() {
        assertThat(PpUtil.usesMarkdown("")).isFalse()
    }

    @Test
    fun `usesMarkdown - возвращает true для смешанного контента`() {
        assertThat(PpUtil.usesMarkdown("Plain text with **bold** and `code`")).isTrue()
    }

    @Test
    fun `explainQualityJson - возвращает grade A для больших значений`() {
        val result1 = PpUtil.explainQualityJson(tokens = 300, len = 2000)
        assertThat(result1).contains("\"grade\":\"A\"")
        assertThat(result1).contains("\"tokens\":300")
        assertThat(result1).contains("\"length\":2000")

        val result2 = PpUtil.explainQualityJson(tokens = 500, len = 1000)
        assertThat(result2).contains("\"grade\":\"A\"")
    }

    @Test
    fun `explainQualityJson - возвращает grade B для средних значений`() {
        val result1 = PpUtil.explainQualityJson(tokens = 120, len = 800)
        assertThat(result1).contains("\"grade\":\"B\"")
        assertThat(result1).contains("\"tokens\":120")
        assertThat(result1).contains("\"length\":800")

        val result2 = PpUtil.explainQualityJson(tokens = 200, len = 500)
        assertThat(result2).contains("\"grade\":\"B\"")
    }

    @Test
    fun `explainQualityJson - возвращает grade C для малых значений`() {
        val result1 = PpUtil.explainQualityJson(tokens = 50, len = 100)
        assertThat(result1).contains("\"grade\":\"C\"")
        assertThat(result1).contains("\"tokens\":50")
        assertThat(result1).contains("\"length\":100")
    }

    @Test
    fun `explainQualityJson - проверяет граничные значения для A`() {
        // tokens >= 300 -> A
        val result1 = PpUtil.explainQualityJson(tokens = 300, len = 1999)
        assertThat(result1).contains("\"grade\":\"A\"")

        // len >= 2000 -> A
        val result2 = PpUtil.explainQualityJson(tokens = 299, len = 2000)
        assertThat(result2).contains("\"grade\":\"A\"")

        // tokens >= 300 && len >= 2000 -> A
        val result3 = PpUtil.explainQualityJson(tokens = 300, len = 2000)
        assertThat(result3).contains("\"grade\":\"A\"")

        // tokens < 300 && len < 2000 -> B (так как tokens >= 120)
        val result4 = PpUtil.explainQualityJson(tokens = 299, len = 1999)
        assertThat(result4).contains("\"grade\":\"B\"")
    }

    @Test
    fun `explainQualityJson - проверяет граничные значения для B`() {
        // tokens >= 120 -> B (но не A, так как tokens < 300 && len < 2000)
        val result1 = PpUtil.explainQualityJson(tokens = 120, len = 799)
        assertThat(result1).contains("\"grade\":\"B\"")

        // len >= 800 -> B (но не A, так как tokens < 300 && len < 2000)
        val result2 = PpUtil.explainQualityJson(tokens = 119, len = 800)
        assertThat(result2).contains("\"grade\":\"B\"")

        // tokens >= 120 && len >= 800 -> B (но не A, так как tokens < 300 && len < 2000)
        val result3 = PpUtil.explainQualityJson(tokens = 120, len = 800)
        assertThat(result3).contains("\"grade\":\"B\"")

        // tokens < 120 && len < 800 -> C
        val result4 = PpUtil.explainQualityJson(tokens = 119, len = 799)
        assertThat(result4).contains("\"grade\":\"C\"")
    }

    @Test
    fun `explainMd - форматирует preview для короткого текста`() {
        val text = "Short text"
        val tokens = 2
        val result = PpUtil.explainMd(text, tokens)
        
        assertThat(result).contains("### Summary")
        assertThat(result).contains("length: ${text.length}")
        assertThat(result).contains("tokens: $tokens")
        assertThat(result).contains("#### Preview")
        assertThat(result).contains(text)
        assertThat(result).doesNotContain("…")
    }

    @Test
    fun `explainMd - обрезает длинный текст до 240 символов`() {
        val longText = "a".repeat(300)
        val tokens = 50
        val result = PpUtil.explainMd(longText, tokens)
        
        assertThat(result).contains("length: 300")
        assertThat(result).contains("tokens: $tokens")
        assertThat(result).contains("#### Preview")
        assertThat(result).contains("a".repeat(240))
        assertThat(result).contains("…")
    }

    @Test
    fun `explainMd - заменяет переносы строк на пробелы`() {
        val text = "Line 1\nLine 2\nLine 3"
        val tokens = 6
        val result = PpUtil.explainMd(text, tokens)
        
        // Проверяем, что в preview переносы строк заменены на пробелы
        assertThat(result).contains("Line 1 Line 2 Line 3")
        // Проверяем, что preview не содержит переносов строк (они заменены на пробелы в take(240))
        val previewStart = result.indexOf("#### Preview\n") + "#### Preview\n".length
        val preview = result.substring(previewStart).trim()
        // Preview должен быть одной строкой без переносов
        assertThat(preview.split("\n").size).isLessThanOrEqualTo(1)
    }

    @Test
    fun `explainMd - обрабатывает текст длиной ровно 240 символов`() {
        val text = "a".repeat(240)
        val tokens = 50
        val result = PpUtil.explainMd(text, tokens)
        
        assertThat(result).contains("a".repeat(240))
        assertThat(result).doesNotContain("…")
    }

    @Test
    fun `explainMd - обрабатывает текст длиной 241 символ`() {
        val text = "a".repeat(241)
        val tokens = 50
        val result = PpUtil.explainMd(text, tokens)
        
        assertThat(result).contains("a".repeat(240))
        assertThat(result).contains("…")
    }

    @Test
    fun `explainMd - обрабатывает пустую строку`() {
        val result = PpUtil.explainMd("", 0)
        
        assertThat(result).contains("length: 0")
        assertThat(result).contains("tokens: 0")
        assertThat(result).doesNotContain("…")
    }

    @Test
    fun `explainMd - обрабатывает текст только с переносами строк`() {
        val text = "\n\n\n"
        val tokens = 0
        val result = PpUtil.explainMd(text, tokens)
        
        assertThat(result).contains("length: 3")
        assertThat(result).contains("### Preview")
        assertThat(result).contains("   ") // Переносы строк заменены на пробелы
    }

    @Test
    fun `explainQualityJson - граничное значение для A tokens = 299 len = 1999 возвращает B`() {
        val result = PpUtil.explainQualityJson(tokens = 299, len = 1999)
        assertThat(result).contains("\"grade\":\"B\"")
    }

    @Test
    fun `explainQualityJson - граничное значение для B tokens = 119 len = 799 возвращает C`() {
        val result = PpUtil.explainQualityJson(tokens = 119, len = 799)
        assertThat(result).contains("\"grade\":\"C\"")
    }

    @Test
    fun `explainQualityJson - tokens = 0 len = 0 возвращает C`() {
        val result = PpUtil.explainQualityJson(tokens = 0, len = 0)
        assertThat(result).contains("\"grade\":\"C\"")
        assertThat(result).contains("\"tokens\":0")
        assertThat(result).contains("\"length\":0")
    }

    @Test
    fun `usesMarkdown - возвращает false для текста с переносами строк без markdown`() {
        val text = "Plain text\nwith newlines\nbut no markdown"
        assertThat(PpUtil.usesMarkdown(text)).isFalse()
    }

    @Test
    fun `usesMarkdown - возвращает true для заголовка с пробелами перед решеткой`() {
        assertThat(PpUtil.usesMarkdown("   # Header")).isTrue()
        assertThat(PpUtil.usesMarkdown("  ## Header")).isTrue()
        assertThat(PpUtil.usesMarkdown(" ### Header")).isTrue()
    }

    @Test
    fun `tokenCount - обрабатывает только пробелы`() {
        assertThat(PpUtil.tokenCount("   ")).isEqualTo(0)
    }

    @Test
    fun `tokenCount - обрабатывает смешанные разделители`() {
        assertThat(PpUtil.tokenCount("hello\tworld\nfoo\rbar")).isEqualTo(4)
    }

    @Test
    fun `spanCharsRange - обрабатывает очень длинную строку`() {
        val longText = "a".repeat(1000000)
        val result = PpUtil.spanCharsRange(longText)
        assertThat(result).isEqualTo("[0,1000000)")
    }
    
    @Test
    fun `usesMarkdown - возвращает true для заголовка с табуляцией`() {
        assertThat(PpUtil.usesMarkdown("\t# Header")).isTrue()
        assertThat(PpUtil.usesMarkdown("\t\t## Header")).isTrue()
    }
    
    @Test
    fun `usesMarkdown - возвращает true для ссылки в тексте`() {
        assertThat(PpUtil.usesMarkdown("See [link](url) for more")).isTrue()
    }
    
    @Test
    fun `usesMarkdown - возвращает true для кода в тексте`() {
        assertThat(PpUtil.usesMarkdown("Use `code()` function")).isTrue()
    }
    
    @Test
    fun `usesMarkdown - возвращает true для списка с отступом`() {
        assertThat(PpUtil.usesMarkdown("  - item")).isTrue()
        assertThat(PpUtil.usesMarkdown("   * item")).isTrue()
    }
    
    @Test
    fun `usesMarkdown - возвращает true для жирного текста в предложении`() {
        assertThat(PpUtil.usesMarkdown("This is **important** text")).isTrue()
        assertThat(PpUtil.usesMarkdown("This is __important__ text")).isTrue()
    }
    
    @Test
    fun `usesMarkdown - возвращает false для текста с решеткой не в начале строки`() {
        assertThat(PpUtil.usesMarkdown("Text # not a header")).isFalse()
    }
    
    @Test
    fun `usesMarkdown - возвращает false для текста с квадратными скобками без ссылки`() {
        assertThat(PpUtil.usesMarkdown("Text [not a link]")).isFalse()
    }
    
    @Test
    fun `usesMarkdown - возвращает false для текста с одинарными кавычками`() {
        assertThat(PpUtil.usesMarkdown("Text 'not code'")).isFalse()
    }
    
    @Test
    fun `usesMarkdown - возвращает false для текста с дефисом не в начале строки`() {
        assertThat(PpUtil.usesMarkdown("Text - not a list")).isFalse()
    }
    
    @Test
    fun `usesMarkdown - возвращает false для текста с звездочкой не в начале строки`() {
        assertThat(PpUtil.usesMarkdown("Text * not a list")).isFalse()
    }
    
    @Test
    fun `explainQualityJson - граничное значение tokens = 300 len = 1999 возвращает A`() {
        val result = PpUtil.explainQualityJson(tokens = 300, len = 1999)
        assertThat(result).contains("\"grade\":\"A\"")
    }
    
    @Test
    fun `explainQualityJson - граничное значение tokens = 299 len = 2000 возвращает A`() {
        val result = PpUtil.explainQualityJson(tokens = 299, len = 2000)
        assertThat(result).contains("\"grade\":\"A\"")
    }
    
    @Test
    fun `explainQualityJson - граничное значение tokens = 120 len = 799 возвращает B`() {
        val result = PpUtil.explainQualityJson(tokens = 120, len = 799)
        assertThat(result).contains("\"grade\":\"B\"")
    }
    
    @Test
    fun `explainQualityJson - граничное значение tokens = 119 len = 800 возвращает B`() {
        val result = PpUtil.explainQualityJson(tokens = 119, len = 800)
        assertThat(result).contains("\"grade\":\"B\"")
    }
    
    @Test
    fun `explainMd - обрабатывает текст с множественными переносами строк`() {
        val text = "Line 1\n\n\nLine 2\n\nLine 3"
        val tokens = 6
        val result = PpUtil.explainMd(text, tokens)
        
        assertThat(result).contains("Line 1")
        assertThat(result).contains("Line 2")
        assertThat(result).contains("Line 3")
    }
    
    @Test
    fun `explainMd - обрабатывает текст с табуляцией`() {
        val text = "Line 1\tLine 2\tLine 3"
        val tokens = 6
        val result = PpUtil.explainMd(text, tokens)
        
        assertThat(result).contains("Line 1")
    }
    
    @Test
    fun `tokenCount - обрабатывает текст с только табуляцией`() {
        assertThat(PpUtil.tokenCount("\t\t\t")).isEqualTo(0)
    }
    
    @Test
    fun `tokenCount - обрабатывает текст с только переносами строк`() {
        assertThat(PpUtil.tokenCount("\n\n\n")).isEqualTo(0)
    }
    
    @Test
    fun `tokenCount - обрабатывает текст с только возвратом каретки`() {
        assertThat(PpUtil.tokenCount("\r\r\r")).isEqualTo(0)
    }
    
    @Test
    fun `tokenCount - обрабатывает текст с смешанными пробелами и табуляцией`() {
        assertThat(PpUtil.tokenCount("hello\tworld\nfoo\rbar")).isEqualTo(4)
    }
    
    @Test
    fun `sha256Hex - обрабатывает очень длинную строку`() {
        val longText = "a".repeat(100000)
        val result = PpUtil.sha256Hex(longText)
        assertThat(result).isNotEmpty
        assertThat(result).hasSize(64)
    }
    
    @Test
    fun `sha256Hex - обрабатывает строку с спецсимволами`() {
        val text = "!@#$%^&*()_+-=[]{}|;':\",./<>?"
        val result = PpUtil.sha256Hex(text)
        assertThat(result).isNotEmpty
        assertThat(result).hasSize(64)
    }
    
    @Test
    fun `sha256Hex - обрабатывает строку с эмодзи`() {
        val text = "Hello 😀 World 🌍"
        val result = PpUtil.sha256Hex(text)
        assertThat(result).isNotEmpty
        assertThat(result).hasSize(64)
    }
    
    @Test
    fun `sha256Hex - детерминированность для одинаковых строк`() {
        val text = "test string with special chars: !@#$%"
        val hash1 = PpUtil.sha256Hex(text)
        val hash2 = PpUtil.sha256Hex(text)
        assertThat(hash1).isEqualTo(hash2)
    }
}
