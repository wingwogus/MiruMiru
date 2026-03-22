package com.example.config

import com.example.api.security.CustomAccessDeniedHandler
import com.example.api.security.CustomAuthenticationEntryPoint
import com.example.api.security.JwtAuthenticationFilter
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration


@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val accessDeniedHandler: CustomAccessDeniedHandler,
    private val authenticationEntryPoint: CustomAuthenticationEntryPoint,
    private val jwtAuthenticationFilter: JwtAuthenticationFilter
) {

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()


    companion object {
        private val PUBLIC_ENDPOINTS = listOf(
            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/api/v1/auth/**",       // 로그인/회원가입/토큰 재발급
            "/chat-test.html",       // local chat test page (static)
            "/ws/**",                // WebSocket handshake
            "/error",                // 스프링 내부 오류 페이지
        )
    }

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {

        http
            .csrf { it.disable() }
            .formLogin { it.disable() }
            .httpBasic { it.disable() }

            .cors {
                it.configurationSource {
                    CorsConfiguration().apply {
                        allowedOriginPatterns = listOf("*")
                        allowedMethods = listOf("*")
                        allowedHeaders = listOf("*")
                        allowCredentials = true
                    }
                }
            }

            .sessionManagement {
                it.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            }

            .exceptionHandling {
                it.authenticationEntryPoint(authenticationEntryPoint)
                it.accessDeniedHandler(accessDeniedHandler)
            }

            .authorizeHttpRequests {
                it
                    .requestMatchers(*PUBLIC_ENDPOINTS.toTypedArray())
                    .permitAll()
                    .anyRequest().authenticated()
            }

            .addFilterBefore(
                jwtAuthenticationFilter,
                UsernamePasswordAuthenticationFilter::class.java
            )

        return http.build()
    }

    @Bean
    fun loggingFilterRegistration(loggingFilter: LoggingFilter): FilterRegistrationBean<LoggingFilter> {
        return FilterRegistrationBean<LoggingFilter>().apply {
            filter = loggingFilter
            order = Int.MIN_VALUE   // 첫 번째로 실행
            addUrlPatterns("/*")
        }
    }

}
