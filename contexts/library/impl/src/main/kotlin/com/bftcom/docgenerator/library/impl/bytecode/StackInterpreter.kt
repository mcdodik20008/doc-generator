package com.bftcom.docgenerator.library.impl.bytecode

import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type

/**
 * Простой интерпретатор стека для извлечения строковых значений (URL, топики и т.д.).
 * Поддерживает базовые операции: LDC строк, конкатенацию через +, StringBuilder.
 * 
 * Это упрощенная версия для MVP - не покрывает все случаи, но обрабатывает большинство простых паттернов.
 */
class StackInterpreter {
    private val stack = mutableListOf<StackValue>()
    
    /**
     * Значение на стеке.
     */
    sealed class StackValue {
        data class StringValue(val value: String) : StackValue()
        data class UnknownValue(val type: String) : StackValue()
        
        fun asString(): String? = when (this) {
            is StringValue -> value
            is UnknownValue -> null
        }
    }
    
    /**
     * Обрабатывает инструкцию LDC (загрузка константы).
     */
    fun visitLdc(cst: Any?) {
        when (cst) {
            is String -> push(StackValue.StringValue(cst))
            is Type -> push(StackValue.UnknownValue("Type"))
            is Int, is Long, is Float, is Double -> push(StackValue.UnknownValue("Number"))
            else -> push(StackValue.UnknownValue("Unknown"))
        }
    }
    
    /**
     * Обрабатывает создание StringBuilder.
     */
    fun visitNewStringBuilder() {
        // StringBuilder создается, но пока не знаем его содержимое
        push(StackValue.UnknownValue("StringBuilder"))
    }
    
    /**
     * Обрабатывает вызов append на StringBuilder.
     */
    fun visitStringBuilderAppend(descriptor: String) {
        if (stack.size < 2) return
        
        val value = stack.removeAt(stack.size - 1)
        val sb = stack.removeAt(stack.size - 1)
        
        // Если StringBuilder был Unknown, но теперь добавляем строку - создаем новый StringBuilder
        val appendedValue = when {
            value is StackValue.StringValue && sb is StackValue.UnknownValue && sb.type == "StringBuilder" -> {
                StackValue.StringValue(value.value)
            }
            value is StackValue.StringValue && sb is StackValue.StringValue -> {
                // Конкатенация двух строк
                StackValue.StringValue(sb.value + value.value)
            }
            else -> StackValue.UnknownValue("StringBuilder")
        }
        
        push(appendedValue)
    }
    
    /**
     * Обрабатывает вызов toString() на StringBuilder.
     */
    fun visitStringBuilderToString() {
        if (stack.isEmpty()) return
        
        val top = stack.removeAt(stack.size - 1)
        // Если это был StringBuilder со строкой, возвращаем строку
        when (top) {
            is StackValue.StringValue -> push(top)
            is StackValue.UnknownValue -> {
                if (top.type == "StringBuilder") {
                    // Не можем определить значение, но это была строка
                    push(StackValue.UnknownValue("String"))
                } else {
                    push(top)
                }
            }
        }
    }
    
    /**
     * Обрабатывает конкатенацию строк через + (invokevirtual String.concat или через StringBuilder).
     */
    fun visitStringConcat() {
        if (stack.size < 2) return
        
        val right = stack.removeAt(stack.size - 1)
        val left = stack.removeAt(stack.size - 1)
        
        val result = when {
            left is StackValue.StringValue && right is StackValue.StringValue -> {
                StackValue.StringValue(left.value + right.value)
            }
            left is StackValue.StringValue && right is StackValue.UnknownValue -> {
                // Левая часть известна, правая нет - сохраняем левую
                left
            }
            right is StackValue.StringValue && left is StackValue.UnknownValue -> {
                // Правая часть известна, левая нет - сохраняем правую
                right
            }
            else -> StackValue.UnknownValue("String")
        }
        
        push(result)
    }
    
    /**
     * Получить строковое значение с вершины стека (если доступно).
     */
    fun peekString(): String? {
        if (stack.isEmpty()) return null
        return stack.last().asString()
    }
    
    /**
     * Получить строковое значение и удалить его со стека.
     */
    fun popString(): String? {
        if (stack.isEmpty()) return null
        val value = stack.removeAt(stack.size - 1)
        return value.asString()
    }
    
    /**
     * Получить строковое значение из аргументов метода (для методов с одним строковым параметром).
     */
    fun popStringArg(): String? {
        // Для простоты берем последнее строковое значение на стеке
        // В реальности нужно учитывать количество аргументов метода
        for (i in stack.indices.reversed()) {
            val value = stack[i].asString()
            if (value != null) {
                stack.removeAt(i)
                return value
            }
        }
        return null
    }
    
    /**
     * Очистить стек (при переходе к новой инструкции или блоку).
     */
    fun clear() {
        stack.clear()
    }
    
    /**
     * Получить текущий размер стека (для отладки).
     */
    fun size(): Int = stack.size
    
    private fun push(value: StackValue) {
        stack.add(value)
        // Ограничиваем размер стека для безопасности
        if (stack.size > 100) {
            stack.removeAt(0)
        }
    }
}

