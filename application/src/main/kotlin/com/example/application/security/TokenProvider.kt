package com.example.application.security

import com.example.application.auth.AuthResult
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
        private const val ROLE_CLAIM = "role"
    }

    private val key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(secretKey))

    /** -------------------------------
     *  AccessToken 생성 (userId만 저장)
     * -------------------------------- */
    fun createAccessToken(userId: Long, role: String): String {
        val now = Date()

        return Jwts.builder()
            .setSubject(userId.toString())      // sub = userId
            .claim(ROLE_CLAIM, role)
            .setIssuedAt(now)
            .setExpiration(Date(now.time + ACCESS_TOKEN_VALIDITY))
            .signWith(key, SignatureAlgorithm.HS512)
            .compact()
    }

    /** -------------------------------
     *  RefreshToken 생성
     * -------------------------------- */
    fun createRefreshToken(userId: Long): String {
        val now = Date()

        return Jwts.builder()
            .setSubject(userId.toString())
            .setIssuedAt(now)
            .setExpiration(Date(now.time + REFRESH_TOKEN_VALIDITY))
            .signWith(key, SignatureAlgorithm.HS512)
            .compact()
    }

    fun generateToken(userId: Long, role: String): AuthResult.TokenPair {
        return AuthResult.TokenPair(
            accessToken = createAccessToken(userId, role),
            refreshToken = createRefreshToken(userId)
        )
    }

    fun getRefreshTokenValiditySeconds(): Long = REFRESH_TOKEN_VALIDITY / 1000

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

    fun getRole(token: String): String? {
        return parseClaims(token)[ROLE_CLAIM] as? String
    }

    fun getAuthentication(token: String): Authentication {
        val userId = getUserId(token)
        val role = getRole(token)

        val authorities = role?.let { listOf(SimpleGrantedAuthority(it)) } ?: emptyList()

        val principal = userId.toString()

        return UsernamePasswordAuthenticationToken(
            principal,
            null,       // credentials는 null
            authorities
        )
    }

}
