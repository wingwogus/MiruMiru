package com.example.config

import com.example.api.security.CustomAccessDeniedHandler
import com.example.api.security.CustomAuthenticationEntryPoint
import com.example.api.security.JwtAuthenticationFilter
import com.example.api.security.OAuth2LoginSuccessHandler
import com.example.application.security.CustomOAuth2UserService
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration


@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val accessDeniedHandler: CustomAccessDeniedHandler,
    private val authenticationEntryPoint: CustomAuthenticationEntryPoint,
    private val jwtAuthenticationFilter: JwtAuthenticationFilter,
    private val customOAuth2UserService: CustomOAuth2UserService,
    private val oAuth2LoginSuccessHandler: OAuth2LoginSuccessHandler
) {


    @Bean
    fun userDetailsService(): UserDetailsService {
        return UserDetailsService { _ ->
            throw UsernameNotFoundException("Not using UserDetailsService in JWT authentication")
        }
    }

    @Bean
    fun authenticationManager(authConfig: AuthenticationConfiguration): AuthenticationManager {
        return authConfig.authenticationManager
    }


    companion object {
        private val PUBLIC_ENDPOINTS = listOf(
            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/api/v1/auth/**",       // 로그인/회원가입/토큰 재발급
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

            .oauth2Login { oauth2 ->
                oauth2
                    .userInfoEndpoint { userInfo ->
                        userInfo.userService(customOAuth2UserService)
                    }
                    .successHandler(oAuth2LoginSuccessHandler)
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
