package com.example.application.auth

import com.example.application.exception.ErrorCode
import com.example.application.exception.business.BusinessException
import com.example.application.redis.RefreshTokenRepository
import com.example.application.security.TokenProvider
import com.example.domain.member.Member
import com.example.domain.member.MemberRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.Duration
import java.util.Optional

class AuthServiceTest {
    private lateinit var tokenProvider: TokenProvider
    private lateinit var emailSender: FakeEmailSender
    private lateinit var emailVerificationRepository: FakeEmailVerificationRepository
    private lateinit var nicknameVerificationRepository: FakeNicknameVerificationRepository
    private lateinit var refreshTokenRepository: FakeRefreshTokenRepository
    private lateinit var memberRepository: MemberRepository
    private lateinit var passwordEncoder: PasswordEncoder
    private lateinit var authService: AuthService

    @BeforeEach
    fun setUp() {
        tokenProvider = TokenProvider(TEST_SECRET)
        emailSender = FakeEmailSender()
        emailVerificationRepository = FakeEmailVerificationRepository()
        nicknameVerificationRepository = FakeNicknameVerificationRepository()
        refreshTokenRepository = FakeRefreshTokenRepository()
        memberRepository = mock(MemberRepository::class.java)
        passwordEncoder = FakePasswordEncoder()
        authService = AuthService(
            jwtTokenProvider = tokenProvider,
            emailSender = emailSender,
            emailVerificationRepository = emailVerificationRepository,
            nicknameVerificationRepository = nicknameVerificationRepository,
            refreshTokenRepository = refreshTokenRepository,
            memberRepository = memberRepository,
            passwordEncoder = passwordEncoder,
            authCodeExpirationMillis = 1_800_000L,
            verifiedStateExpirationMillis = 1_800_000L
        )
    }

    @Test
    fun `login stores refresh token and embeds member role into access token`() {
        val member = member(role = "ROLE_ADMIN")
        `when`(memberRepository.findByEmail(member.email)).thenReturn(member)

        val result = authService.login(AuthCommand.Login(member.email, "raw-password"))

        assertEquals(member.id, tokenProvider.getUserId(result.accessToken))
        assertEquals(member.role, tokenProvider.getRole(result.accessToken))
        assertEquals(result.refreshToken, refreshTokenRepository.get(member.id))
        assertEquals(tokenProvider.getRefreshTokenValiditySeconds(), refreshTokenRepository.lastSavedTtl)
    }

    @Test
    fun `reissue ignores access token identity and uses refresh token subject`() {
        val member = member(role = "ROLE_ADMIN")
        val refreshToken = tokenProvider.createRefreshToken(member.id)
        refreshTokenRepository.save(member.id, refreshToken, tokenProvider.getRefreshTokenValiditySeconds())
        `when`(memberRepository.findById(member.id)).thenReturn(Optional.of(member))

        val result = authService.reissue(AuthCommand.Reissue("invalid-access-token", refreshToken))

        assertEquals(member.id, tokenProvider.getUserId(result.accessToken))
        assertEquals(member.role, tokenProvider.getRole(result.accessToken))
        assertEquals(result.refreshToken, refreshTokenRepository.get(member.id))
        assertEquals(tokenProvider.getRefreshTokenValiditySeconds(), refreshTokenRepository.lastSavedTtl)
    }

    @Test
    fun `reissue fails when stored refresh token does not match`() {
        val refreshToken = tokenProvider.createRefreshToken(1L)
        refreshTokenRepository.save(1L, "different-token", tokenProvider.getRefreshTokenValiditySeconds())

        val exception = assertThrows(BusinessException::class.java) {
            authService.reissue(AuthCommand.Reissue("ignored-access-token", refreshToken))
        }

        assertEquals(ErrorCode.UNAUTHORIZED, exception.errorCode)
    }

    @Test
    fun `logout deletes stored refresh token`() {
        refreshTokenRepository.save(1L, "stored-refresh", tokenProvider.getRefreshTokenValiditySeconds())

        authService.logout("1")

        assertEquals(null, refreshTokenRepository.get(1L))
    }

    @Test
    fun `send code stores verification code and sends email`() {
        `when`(memberRepository.existsByEmail("user@tokyo.ac.jp")).thenReturn(false)

        authService.sendCodeToEmail("user@tokyo.ac.jp")

        assertEquals("user@tokyo.ac.jp", emailSender.sentEmail)
        assertEquals(emailSender.sentCode, emailVerificationRepository.getCode("user@tokyo.ac.jp"))
        assertEquals(Duration.ofMillis(1_800_000L), emailVerificationRepository.lastSavedTtl)
    }

    @Test
    fun `login returns unauthorized when email does not exist`() {
        `when`(memberRepository.findByEmail("missing@tokyo.ac.jp")).thenReturn(null)

        val exception = assertThrows(BusinessException::class.java) {
            authService.login(AuthCommand.Login("missing@tokyo.ac.jp", "raw-password"))
        }

        assertEquals(ErrorCode.UNAUTHORIZED, exception.errorCode)
    }

    @Test
    fun `nickname verification stores verified state with ttl`() {
        `when`(memberRepository.existsByNickname("maru")).thenReturn(false)

        authService.checkDuplicatedNickname(AuthCommand.VerifyNickname("maru"))

        assertEquals(true, nicknameVerificationRepository.isVerified("maru"))
        assertEquals(Duration.ofMillis(1_800_000L), nicknameVerificationRepository.lastSavedTtl)
    }

    @Test
    fun `signup requires nickname verification and clears verification state on success`() {
        val request = AuthCommand.SignUp(
            email = "user@tokyo.ac.jp",
            password = "raw-password",
            nickname = "maru"
        )
        emailVerificationRepository.saveCode(request.email, "123456", Duration.ofMillis(1_800_000L))
        emailVerificationRepository.markVerified(request.email, Duration.ofMillis(1_800_000L))
        nicknameVerificationRepository.markVerified(request.nickname, Duration.ofMillis(1_800_000L))
        `when`(memberRepository.existsByEmail(request.email)).thenReturn(false)
        `when`(memberRepository.existsByNickname(request.nickname)).thenReturn(false)
        `when`(memberRepository.save(org.mockito.ArgumentMatchers.any(Member::class.java)))
            .thenAnswer { it.arguments.first() }

        authService.signUp(request)

        assertEquals(false, emailVerificationRepository.isVerified(request.email))
        assertEquals(null, emailVerificationRepository.getCode(request.email))
        assertEquals(false, nicknameVerificationRepository.isVerified(request.nickname))
    }

    @Test
    fun `signup fails when nickname verification state is missing`() {
        val request = AuthCommand.SignUp(
            email = "user@tokyo.ac.jp",
            password = "raw-password",
            nickname = "maru"
        )
        emailVerificationRepository.markVerified(request.email, Duration.ofMillis(1_800_000L))

        val exception = assertThrows(BusinessException::class.java) {
            authService.signUp(request)
        }

        assertEquals(ErrorCode.INVALID_INPUT, exception.errorCode)
    }

    companion object {
        private const val TEST_SECRET =
            "t2oRk29vBQZWS8GEt4xr8AJznlPK0ipBKUwdyqe10SOGZB26vVBMjzqualdJsjcOY1wX9DOqJC9V1DFl58F0tQ=="

        private fun member(
            id: Long = 1L,
            email: String = "user@tokyo.ac.jp",
            password: String = "encoded:raw-password",
            nickname: String = "maru",
            role: String = "ROLE_USER"
        ): Member {
            return Member(
                id = id,
                email = email,
                password = password,
                nickname = nickname,
                role = role
            )
        }
    }

    private class FakeEmailSender : EmailSender {
        var sentEmail: String? = null
        var sentCode: String? = null

        override fun sendAuthCode(email: String, code: String) {
            sentEmail = email
            sentCode = code
        }
    }

    private class FakeEmailVerificationRepository : EmailVerificationRepository {
        private val codes = mutableMapOf<String, String>()
        private val verified = mutableSetOf<String>()
        var lastSavedTtl: Duration? = null
        var lastVerifiedTtl: Duration? = null

        override fun saveCode(email: String, code: String, ttl: Duration) {
            codes[email] = code
            verified.remove(email)
            lastSavedTtl = ttl
        }

        override fun getCode(email: String): String? = codes[email]

        override fun markVerified(email: String, ttl: Duration) {
            verified.add(email)
            lastVerifiedTtl = ttl
        }

        override fun isVerified(email: String): Boolean = verified.contains(email)

        override fun deleteCode(email: String) {
            codes.remove(email)
        }

        override fun deleteVerified(email: String) {
            verified.remove(email)
        }
    }

    private class FakeNicknameVerificationRepository : NicknameVerificationRepository {
        private val verified = mutableSetOf<String>()
        var lastSavedTtl: Duration? = null

        override fun markVerified(nickname: String, ttl: Duration) {
            verified.add(nickname)
            lastSavedTtl = ttl
        }

        override fun isVerified(nickname: String): Boolean = verified.contains(nickname)

        override fun delete(nickname: String) {
            verified.remove(nickname)
        }
    }

    private class FakeRefreshTokenRepository : RefreshTokenRepository {
        private val tokens = mutableMapOf<Long, String>()
        var lastSavedTtl: Long? = null

        override fun save(userId: Long, refreshToken: String, expiresInSeconds: Long) {
            tokens[userId] = refreshToken
            lastSavedTtl = expiresInSeconds
        }

        override fun get(userId: Long): String? = tokens[userId]

        override fun delete(userId: Long) {
            tokens.remove(userId)
        }
    }

    private class FakePasswordEncoder : PasswordEncoder {
        override fun encode(rawPassword: CharSequence): String = "encoded:$rawPassword"

        override fun matches(rawPassword: CharSequence, encodedPassword: String): Boolean {
            return encodedPassword == encode(rawPassword)
        }
    }
}
