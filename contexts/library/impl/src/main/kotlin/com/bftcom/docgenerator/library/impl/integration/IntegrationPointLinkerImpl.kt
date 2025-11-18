package com.bftcom.docgenerator.library.impl.integration

import com.bftcom.docgenerator.db.EdgeRepository
import com.bftcom.docgenerator.db.LibraryNodeRepository
import com.bftcom.docgenerator.db.NodeRepository
import com.bftcom.docgenerator.domain.application.Application
import com.bftcom.docgenerator.domain.enums.EdgeKind
import com.bftcom.docgenerator.domain.enums.NodeKind
import com.bftcom.docgenerator.library.api.integration.IntegrationLinkResult
import com.bftcom.docgenerator.library.api.integration.IntegrationPoint
import com.bftcom.docgenerator.library.api.integration.IntegrationPointLinker
import com.bftcom.docgenerator.library.api.integration.IntegrationPointService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Реализация линкера интеграционных точек.
 * 
 * Создает Edge между методами приложения и интеграционными точками из библиотек.
 * 
 * Пока упрощенная версия - создает виртуальные узлы для интеграционных точек
 * и связывает с ними методы приложения.
 */
@Service
class IntegrationPointLinkerImpl(
    private val nodeRepo: NodeRepository,
    private val libraryNodeRepo: LibraryNodeRepository,
    private val edgeRepo: EdgeRepository,
    private val integrationPointService: IntegrationPointService,
) : IntegrationPointLinker {
    private val log = LoggerFactory.getLogger(javaClass)
    
    @Transactional
    override fun linkIntegrationPoints(application: Application): IntegrationLinkResult {
        log.info("Linking integration points for application: {}", application.key)
        
        var httpEdgesCreated = 0
        var kafkaEdgesCreated = 0
        var camelEdgesCreated = 0
        val errors = mutableListOf<String>()
        
        // 1. Находим все методы приложения
        val appMethods = nodeRepo.findAllByApplicationIdAndKindIn(
            application.id!!,
            setOf(NodeKind.METHOD),
            org.springframework.data.domain.PageRequest.of(0, Int.MAX_VALUE),
        )
        
        log.info("Found {} methods in application", appMethods.size)
        
        // 2. Для каждого метода приложения ищем вызовы методов библиотек
        // Пока упрощенная версия - ищем по FQN методов библиотек
        // В будущем можно улучшить, анализируя call graph
        
        // 3. Находим все родительские клиенты в библиотеках
        // TODO: нужно знать, какие библиотеки используются приложением
        // Пока берем все библиотеки
        
        // Упрощенная версия: создаем связи на основе анализа метаданных
        // В реальности нужно анализировать call graph между приложением и библиотеками
        
        log.info(
            "Integration linking completed: http={}, kafka={}, camel={}, errors={}",
            httpEdgesCreated,
            kafkaEdgesCreated,
            camelEdgesCreated,
            errors.size,
        )
        
        return IntegrationLinkResult(
            httpEdgesCreated = httpEdgesCreated,
            kafkaEdgesCreated = kafkaEdgesCreated,
            camelEdgesCreated = camelEdgesCreated,
            errors = errors,
        )
    }
    
    /**
     * Создает Edge между методом приложения и интеграционной точкой.
     * 
     * Для HTTP endpoints создает виртуальный узел типа ENDPOINT и связь CALLS_HTTP.
     * Для Kafka topics создает виртуальный узел типа TOPIC и связь PRODUCES/CONSUMES.
     */
    private fun createIntegrationEdge(
        appMethod: com.bftcom.docgenerator.domain.node.Node,
        point: IntegrationPoint,
    ): Boolean {
        return when (point) {
            is IntegrationPoint.HttpEndpoint -> {
                // TODO: создать или найти узел ENDPOINT для URL
                // Пока просто логируем
                log.debug(
                    "Would create CALLS_HTTP edge: {} -> {} ({})",
                    appMethod.fqn,
                    point.url,
                    point.httpMethod,
                )
                false // пока не создаем
            }
            is IntegrationPoint.KafkaTopic -> {
                // TODO: создать или найти узел TOPIC
                log.debug(
                    "Would create {} edge: {} -> {}",
                    if (point.operation == "PRODUCE") "PRODUCES" else "CONSUMES",
                    appMethod.fqn,
                    point.topic,
                )
                false // пока не создаем
            }
            is IntegrationPoint.CamelRoute -> {
                // TODO: создать или найти узел для Camel route
                log.debug(
                    "Would create Camel edge: {} -> {} ({})",
                    appMethod.fqn,
                    point.uri,
                    point.direction,
                )
                false // пока не создаем
            }
        }
    }
}

