package com.bftcom.docgenerator.graph.impl.node.builder.validation

import com.bftcom.docgenerator.domain.node.Node
import com.bftcom.docgenerator.graph.api.node.NodeValidator
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Реализация валидатора узлов.
 */
@Component
class NodeValidatorImpl : NodeValidator {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val MAX_FQN_LENGTH = 1000
        // Поддерживает:
        // - Базовый формат: package.ClassName или package.ClassName.method
        // - Формат функций: package.ClassName.method(Type1,Type2) или package.method(Type1,Type2)
        private val FQN_PATTERN = Regex("^[a-zA-Z_][a-zA-Z0-9_.]*(?:\\([a-zA-Z0-9_,. ]*\\))?$")
    }

    override fun validate(
        fqn: String,
        span: IntRange?,
        parent: Node?,
        sourceCode: String?,
        applicationId: Long,
    ) {
        try {
            // Валидация FQN
            require(fqn.isNotBlank()) { "FQN cannot be blank" }
            require(fqn.length <= MAX_FQN_LENGTH) {
                "FQN is too long: ${fqn.length} characters (max $MAX_FQN_LENGTH)"
            }

            // Валидация формата FQN (базовая проверка)
            require(fqn.matches(FQN_PATTERN)) {
                "FQN has invalid format: $fqn (must start with letter/underscore, contain only alphanumeric, dots, underscores, and optionally function parameters in parentheses)"
            }

            // Валидация диапазона строк
            span?.let {
                require(it.first >= 0) { "lineStart must be non-negative, got ${it.first}" }
                require(it.first <= it.last) {
                    "lineStart (${it.first}) must be <= lineEnd (${it.last})"
                }
            }

            // Валидация parent
            parent?.let {
                require(it.application.id == applicationId) {
                    "Parent node (${it.fqn}) must belong to the same application ($applicationId)"
                }

                // Проверка, что parent не является самим узлом (защита от самоссылки)
                require(it.fqn != fqn) {
                    "Node cannot be its own parent: fqn=$fqn"
                }
                
                // Примечание: не проверяем it.id != null, так как родитель может быть
                // только что создан в текущей транзакции и еще не иметь id.
                // JPA/Hibernate сам обработает связи при сохранении.
            }

            // Валидация размера sourceCode (предупреждение, не ошибка)
            sourceCode?.let {
                // Размер проверяется в CodeNormalizer, здесь только логируем
                log.trace("Source code validation: fqn={}, size={} bytes", fqn, it.length)
            }
        } catch (e: IllegalArgumentException) {
            log.error("Validation failed for node: fqn={}, error={}", fqn, e.message)
            throw e
        }
    }
}

