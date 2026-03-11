package com.example.api.security

import com.example.application.security.CustomUserDetails
import com.example.application.security.TokenProvider
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.core.Authentication
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler
import org.springframework.stereotype.Component
import org.springframework.web.util.UriComponentsBuilder

@Component
class OAuth2LoginSuccessHandler(
    private val tokenProvider: TokenProvider,
    @Value("\${app.url:http://localhost:3000}") private val appUrl: String // 기본값 설정
) : SimpleUrlAuthenticationSuccessHandler() {

    override fun onAuthenticationSuccess(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authentication: Authentication
    ) {
        val oAuth2User = authentication.principal as CustomUserDetails

        val userId = oAuth2User.memberDto.id

        val accessToken = tokenProvider.createAccessToken(userId) // Role 하드코딩 or 가져오기
        val refreshToken = tokenProvider.createRefreshToken()

        // 리다이렉트
        val targetUrl = UriComponentsBuilder.fromUriString("$appUrl/oauth/callback")
            .queryParam("accessToken", accessToken)
            .queryParam("refreshToken", refreshToken)
            .build().toUriString()

        redirectStrategy.sendRedirect(request, response, targetUrl)
    }
}