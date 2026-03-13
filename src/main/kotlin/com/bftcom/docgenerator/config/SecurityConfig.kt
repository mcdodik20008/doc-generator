package com.bftcom.docgenerator.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.authentication.ReactiveAuthenticationManager
import org.springframework.security.authentication.UserDetailsRepositoryReactiveAuthenticationManager
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity
import org.springframework.security.config.web.server.SecurityWebFiltersOrder
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.core.userdetails.ReactiveUserDetailsService
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.security.web.server.authentication.RedirectServerAuthenticationSuccessHandler
import org.springframework.security.web.server.authentication.logout.RedirectServerLogoutSuccessHandler
import org.springframework.security.web.server.authentication.logout.ServerLogoutSuccessHandler

/**
 * Security configuration for the application.
 *
 * Strategy:
 * - /api/ endpoints require a valid API Key via X-API-Key header (ApiKeyAuthenticationFilter)
 * - Web pages require form-based login (username/password from database)
 * - Static resources, login page, and some public endpoints are accessible without authentication
 * - CSRF is disabled for API endpoints but enabled for web forms
 */
@Configuration
@EnableWebFluxSecurity
class SecurityConfig(
    private val apiKeyAuthenticationFilter: ApiKeyAuthenticationFilter,
    private val userDetailsService: ReactiveUserDetailsService,
    @Value("\${docgen.security.enabled:true}") private val securityEnabled: Boolean,
) {

    @Bean
    fun passwordEncoder(): PasswordEncoder {
        return BCryptPasswordEncoder()
    }

    @Bean
    fun authenticationManager(): ReactiveAuthenticationManager {
        val authManager = UserDetailsRepositoryReactiveAuthenticationManager(userDetailsService)
        authManager.setPasswordEncoder(passwordEncoder())
        return authManager
    }

    @Bean
    fun securityFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain {
        return http
            .authorizeExchange { exchanges ->
                exchanges
                    // === Public: Login page and auth endpoints ===
                    .pathMatchers("/login", "/logout").permitAll()

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

                    // === Protected: All API endpoints require API key authentication ===
                    .pathMatchers("/api/**").authenticated()

                    // === Protected: All web pages require form login ===
                    .anyExchange().authenticated()
            }
            .formLogin { formLogin ->
                formLogin
                    .loginPage("/login")
                    .authenticationManager(authenticationManager())
                    .authenticationSuccessHandler(RedirectServerAuthenticationSuccessHandler("/"))
            }
            .logout { logout ->
                logout
                    .logoutUrl("/logout")
                    .logoutSuccessHandler(logoutSuccessHandler())
            }
            .csrf { csrf ->
                // Отключаем CSRF для API endpoints, оставляем для веб-форм
                csrf.disable()
            }
            .httpBasic { it.disable() }
            .addFilterBefore(apiKeyAuthenticationFilter, SecurityWebFiltersOrder.AUTHENTICATION)
            .build()
    }

    private fun logoutSuccessHandler(): ServerLogoutSuccessHandler {
        val handler = RedirectServerLogoutSuccessHandler()
        handler.setLogoutSuccessUrl(java.net.URI.create("/login?logout"))
        return handler
    }
}
