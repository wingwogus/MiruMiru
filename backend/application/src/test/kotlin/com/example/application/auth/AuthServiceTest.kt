package com.example.application.auth

import com.example.application.exception.ErrorCode
import com.example.application.exception.business.BusinessException
import com.example.application.redis.RefreshTokenRepository
import com.example.application.security.TokenProvider
import com.example.domain.major.Major
import com.example.domain.major.MajorRepository
import com.example.domain.member.Member
import com.example.domain.member.MemberRepository
import com.example.domain.university.University
import com.example.domain.university.UniversityRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
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
    private lateinit var majorRepository: MajorRepository
    private lateinit var universityRepository: UniversityRepository
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
        majorRepository = mock(MajorRepository::class.java)
        universityRepository = mock(UniversityRepository::class.java)
        passwordEncoder = FakePasswordEncoder()
        authService = AuthService(
            jwtTokenProvider = tokenProvider,
            emailSender = emailSender,
            emailVerificationRepository = emailVerificationRepository,
            nicknameVerificationRepository = nicknameVerificationRepository,
            refreshTokenRepository = refreshTokenRepository,
            memberRepository = memberRepository,
            majorRepository = majorRepository,
            universityRepository = universityRepository,
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
        val major = major()
        val request = AuthCommand.SignUp(
            email = "user@tokyo.ac.jp",
            password = "raw-password",
            nickname = "maru",
            majorId = major.id
        )
        emailVerificationRepository.saveCode(request.email, "123456", Duration.ofMillis(1_800_000L))
        emailVerificationRepository.markVerified(request.email, Duration.ofMillis(1_800_000L))
        nicknameVerificationRepository.markVerified(request.nickname, Duration.ofMillis(1_800_000L))
        `when`(memberRepository.existsByEmail(request.email)).thenReturn(false)
        `when`(memberRepository.existsByNickname(request.nickname)).thenReturn(false)
        `when`(universityRepository.findByEmailDomain("tokyo.ac.jp")).thenReturn(university())
        `when`(majorRepository.findByIdAndUniversityId(major.id, 1L)).thenReturn(major)
        `when`(memberRepository.save(org.mockito.ArgumentMatchers.any(Member::class.java)))
            .thenAnswer { it.arguments.first() }

        authService.signUp(request)

        val memberCaptor = ArgumentCaptor.forClass(Member::class.java)
        verify(memberRepository).save(memberCaptor.capture())
        assertEquals("tokyo.ac.jp", memberCaptor.value.university.emailDomain)
        assertEquals(major.id, memberCaptor.value.major.id)
        assertEquals(false, emailVerificationRepository.isVerified(request.email))
        assertEquals(null, emailVerificationRepository.getCode(request.email))
        assertEquals(false, nicknameVerificationRepository.isVerified(request.nickname))
    }

    @Test
    fun `signup fails when nickname verification state is missing`() {
        val request = AuthCommand.SignUp(
            email = "user@tokyo.ac.jp",
            password = "raw-password",
            nickname = "maru",
            majorId = 10L
        )
        emailVerificationRepository.markVerified(request.email, Duration.ofMillis(1_800_000L))

        val exception = assertThrows(BusinessException::class.java) {
            authService.signUp(request)
        }

        assertEquals(ErrorCode.INVALID_INPUT, exception.errorCode)
    }

    @Test
    fun `signup fails when university is not registered`() {
        val request = AuthCommand.SignUp(
            email = "user@kyoto.ac.jp",
            password = "raw-password",
            nickname = "maru",
            majorId = 10L
        )
        emailVerificationRepository.markVerified(request.email, Duration.ofMillis(1_800_000L))
        nicknameVerificationRepository.markVerified(request.nickname, Duration.ofMillis(1_800_000L))
        `when`(memberRepository.existsByEmail(request.email)).thenReturn(false)
        `when`(memberRepository.existsByNickname(request.nickname)).thenReturn(false)
        `when`(universityRepository.findByEmailDomain("kyoto.ac.jp")).thenReturn(null)

        val exception = assertThrows(BusinessException::class.java) {
            authService.signUp(request)
        }

        assertEquals(ErrorCode.UNREGISTERED_UNIVERSITY, exception.errorCode)
    }

    @Test
    fun `signup fails when major does not belong to resolved university`() {
        val request = AuthCommand.SignUp(
            email = "user@tokyo.ac.jp",
            password = "raw-password",
            nickname = "maru",
            majorId = 99L
        )
        emailVerificationRepository.markVerified(request.email, Duration.ofMillis(1_800_000L))
        nicknameVerificationRepository.markVerified(request.nickname, Duration.ofMillis(1_800_000L))
        `when`(memberRepository.existsByEmail(request.email)).thenReturn(false)
        `when`(memberRepository.existsByNickname(request.nickname)).thenReturn(false)
        `when`(universityRepository.findByEmailDomain("tokyo.ac.jp")).thenReturn(university())
        `when`(majorRepository.findByIdAndUniversityId(request.majorId, 1L)).thenReturn(null)

        val exception = assertThrows(BusinessException::class.java) {
            authService.signUp(request)
        }

        assertEquals(ErrorCode.INVALID_MAJOR_SELECTION, exception.errorCode)
    }

    @Test
    fun `get available majors returns university scoped options sorted by name`() {
        val university = university()
        val computerScience = major(id = 10L, university = university, code = "CS", name = "Computer Science")
        val mathematics = major(id = 11L, university = university, code = "MATH", name = "Mathematics")
        `when`(universityRepository.findByEmailDomain("tokyo.ac.jp")).thenReturn(university)
        `when`(majorRepository.findAllByUniversityIdOrderByNameAsc(university.id))
            .thenReturn(listOf(computerScience, mathematics))

        val result = authService.getAvailableMajors("user@tokyo.ac.jp")

        assertEquals(listOf(10L, 11L), result.map { it.majorId })
        assertEquals(listOf("CS", "MATH"), result.map { it.code })
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
                university = university(),
                major = major(university = university()),
                email = email,
                password = password,
                nickname = nickname,
                role = role
            )
        }

        private fun major(
            id: Long = 10L,
            university: University = university(),
            code: String = "CS",
            name: String = "Computer Science"
        ): Major {
            return Major(
                id = id,
                university = university,
                code = code,
                name = name
            )
        }

        private fun university(
            id: Long = 1L,
            name: String = "The University of Tokyo",
            emailDomain: String = "tokyo.ac.jp"
        ): University {
            return University(
                id = id,
                name = name,
                emailDomain = emailDomain
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
