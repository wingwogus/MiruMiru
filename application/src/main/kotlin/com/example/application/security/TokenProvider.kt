package com.example.application.security

import io.jsonwebtoken.*
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.stereotype.Component
import org.springframework.security.core.Authentication
import org.springframework.security.core.authority.SimpleGrantedAuthority
import java.util.*

@Component
class TokenProvider(
    @Value("\${jwt.secret}") secretKey: String
) {

    companion object {
        private const val ACCESS_TOKEN_VALIDITY =
            1000L * 60 * 60 * 24 * 7   // 7일
        private const val REFRESH_TOKEN_VALIDITY =
            1000L * 60 * 60 * 24 * 14 // 14일
    }

    private val key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(secretKey))

    /** -------------------------------
     *  AccessToken 생성 (userId만 저장)
     * -------------------------------- */
    fun createAccessToken(userId: Long): String {
        val now = Date()

        return Jwts.builder()
            .setSubject(userId.toString())      // sub = userId
            .setIssuedAt(now)
            .setExpiration(Date(now.time + ACCESS_TOKEN_VALIDITY))
            .signWith(key, SignatureAlgorithm.HS512)
            .compact()
    }

    /** -------------------------------
     *  RefreshToken 생성
     * -------------------------------- */
    fun createRefreshToken(): String {
        val now = Date()

        return Jwts.builder()
            .setIssuedAt(now)
            .setExpiration(Date(now.time + REFRESH_TOKEN_VALIDITY))
            .signWith(key, SignatureAlgorithm.HS512)
            .compact()
    }

    /** -------------------------------
     *  JWT 유효성 검증
     * -------------------------------- */
    fun validateToken(token: String): Boolean {
        return try {
            Jwts.parserBuilder().setSigningKey(key).build()
                .parseClaimsJws(token)
            true
        } catch (e: JwtException) {
            false
        } catch (e: IllegalArgumentException) {
            false
        }
    }

    /** -------------------------------
     *  Claims 추출
     * -------------------------------- */
    fun parseClaims(token: String): Claims {
        return try {
            Jwts.parserBuilder().setSigningKey(key).build()
                .parseClaimsJws(token).body
        } catch (e: ExpiredJwtException) {
            e.claims
        }
    }

    /** -------------------------------
     *  userId 추출
     * -------------------------------- */
    fun getUserId(token: String): Long {
        return parseClaims(token).subject.toLong()
    }

    fun getAuthentication(token: String): Authentication {
        val userId = getUserId(token)

        // 우리는 권한 정보를 JWT에 넣지 않으므로 기본 USER 권한만 부여
        val authorities = listOf(SimpleGrantedAuthority("ROLE_USER"))

        // principal: userId만 가지는 커스텀 principal
        val principal = userId.toString()

        return UsernamePasswordAuthenticationToken(
            principal,
            null,       // credentials는 null
            authorities
        )
    }

}