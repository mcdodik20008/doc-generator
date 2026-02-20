package com.bftcom.docgenerator.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity
import org.springframework.security.config.web.server.SecurityWebFiltersOrder
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.web.server.SecurityWebFilterChain

/**
 * Security configuration for the application.
 *
 * Strategy:
 * - All Thymeleaf pages, Swagger UI, Actuator, and static resources are publicly accessible
 * - All /api/ endpoints require a valid API Key via X-API-Key header
 * - CSRF is disabled (API-only usage)
 */
@Configuration
@EnableWebFluxSecurity
class SecurityConfig(
    private val apiKeyAuthenticationFilter: ApiKeyAuthenticationFilter,
) {

    @Bean
    fun securityFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain {
        return http
            .csrf { it.disable() }
            .httpBasic { it.disable() }
            .formLogin { it.disable() }
            .authorizeExchange { exchanges ->
                exchanges
                    // === Public: Thymeleaf pages ===
                    .pathMatchers("/", "/dashboard", "/graph", "/chat", "/health", "/ingest").permitAll()

                    // === Public: Static resources ===
                    .pathMatchers("/static/**", "/css/**", "/js/**", "/favicon.svg").permitAll()

                    // === Public: Swagger / OpenAPI ===
                    .pathMatchers("/swagger/**", "/swagger-ui/**", "/swagger-ui.html",
                        "/v3/api-docs/**", "/webjars/**").permitAll()

                    // === Public: Actuator (health, metrics, etc.) ===
                    .pathMatchers("/actuator/**").permitAll()

                    // === Public: Internal endpoints ===
                    .pathMatchers("/internal/**").permitAll()

                    // === Public: OPTIONS for CORS preflight ===
                    .pathMatchers(HttpMethod.OPTIONS).permitAll()

                    // === Protected: All API endpoints require authentication ===
                    .pathMatchers("/api/**").authenticated()

                    // === Everything else: permit (error pages, etc.) ===
                    .anyExchange().permitAll()
            }
            .addFilterBefore(apiKeyAuthenticationFilter, SecurityWebFiltersOrder.AUTHENTICATION)
            .build()
    }
}
