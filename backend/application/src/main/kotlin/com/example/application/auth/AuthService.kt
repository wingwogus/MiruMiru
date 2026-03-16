package com.example.application.auth

import com.example.application.exception.ErrorCode
import com.example.application.exception.business.BusinessException
import com.example.application.redis.RefreshTokenRepository
import com.example.application.security.TokenProvider
import com.example.domain.member.Member
import com.example.domain.member.MemberRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import kotlin.collections.joinToString
import kotlin.jvm.javaClass
import kotlin.random.Random

@Service
@Transactional
class AuthService(
    private val jwtTokenProvider: TokenProvider,
    private val emailSender: EmailSender,
    private val emailVerificationRepository: EmailVerificationRepository,
    private val nicknameVerificationRepository: NicknameVerificationRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val memberRepository: MemberRepository,
    private val passwordEncoder: PasswordEncoder,
    @Value("\${spring.mail.auth-code-expiration-millis}")
    private val authCodeExpirationMillis: Long,
    @Value("\${spring.mail.verified-state-expiration-millis:\${spring.mail.auth-code-expiration-millis}}")
    private val verifiedStateExpirationMillis: Long
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val USER_ROLE = "ROLE_USER"
    }

    fun login(request: AuthCommand.Login): AuthResult.TokenPair {
        val member = memberRepository.findByEmail(request.email)
            ?: throw BusinessException(ErrorCode.UNAUTHORIZED)

        if (!passwordEncoder.matches(request.password, member.password)) {
            throw BusinessException(ErrorCode.UNAUTHORIZED)
        }

        val token = jwtTokenProvider.generateToken(member.id, member.role)
        refreshTokenRepository.save(member.id, token.refreshToken, jwtTokenProvider.getRefreshTokenValiditySeconds())
        logger.info("User logged in successfully. Email hashcode: {}", request.email.hashCode())
        return token
    }

    fun reissue(request: AuthCommand.Reissue): AuthResult.TokenPair {
        if (!jwtTokenProvider.validateToken(request.refreshToken)) {
            throw BusinessException(ErrorCode.UNAUTHORIZED)
        }

        val userId = jwtTokenProvider.getUserId(request.refreshToken)
        val storedRefreshToken = refreshTokenRepository.get(userId)
            ?: throw BusinessException(ErrorCode.UNAUTHORIZED)

        if (storedRefreshToken != request.refreshToken) {
            throw BusinessException(ErrorCode.UNAUTHORIZED)
        }

        val member = memberRepository.findById(userId).orElseThrow {
            BusinessException(ErrorCode.USER_NOT_FOUND)
        }
        val newToken = jwtTokenProvider.generateToken(member.id, member.role)
        refreshTokenRepository.save(member.id, newToken.refreshToken, jwtTokenProvider.getRefreshTokenValiditySeconds())
        logger.info("Token reissued successfully")
        return newToken
    }

    fun signUp(request: AuthCommand.SignUp) {
        if (!emailVerificationRepository.isVerified(request.email)) {
            throw BusinessException(ErrorCode.EMAIL_NOT_VERIFIED)
        }

        if (!nicknameVerificationRepository.isVerified(request.nickname)) {
            throw BusinessException(ErrorCode.INVALID_INPUT)
        }

        if (memberRepository.existsByEmail(request.email)) {
            throw BusinessException(ErrorCode.ALREADY_SIGNED_EMAIL)
        }

        if (memberRepository.existsByNickname(request.nickname)) {
            throw BusinessException(ErrorCode.DUPLICATE_NICKNAME)
        }

        val member = Member(
            email = request.email,
            password = passwordEncoder.encode(request.password),
            nickname = request.nickname,
            role = USER_ROLE
        )
        memberRepository.save(member)
        emailVerificationRepository.deleteCode(request.email)
        emailVerificationRepository.deleteVerified(request.email)
        nicknameVerificationRepository.delete(request.nickname)
        logger.info("User signed up successfully. Email: hashcode {}", request.email.hashCode())
    }

    fun sendCodeToEmail(toEmail: String) {
        checkDuplicatedEmail(toEmail)
        val authCode = createCode()
        emailSender.sendAuthCode(toEmail, authCode)
        emailVerificationRepository.saveCode(toEmail, authCode, Duration.ofMillis(authCodeExpirationMillis))
        logger.info("Authentication code sent to email: {}", toEmail.hashCode())
    }

    fun logout(userId: String) {
        val parsedUserId = userId.toLongOrNull()
            ?: throw BusinessException(ErrorCode.UNAUTHORIZED)

        if (refreshTokenRepository.get(parsedUserId) == null) {
            throw BusinessException(ErrorCode.ALREADY_LOGGED_OUT)
        }
        refreshTokenRepository.delete(parsedUserId)
        logger.info("User logged out successfully. UserId hashcode: {}", userId.hashCode())
    }

    fun checkDuplicatedNickname(request: AuthCommand.VerifyNickname) {
        if (memberRepository.existsByNickname(request.nickname)) {
            throw BusinessException(ErrorCode.DUPLICATE_NICKNAME)
        }
        nicknameVerificationRepository.markVerified(
            request.nickname,
            Duration.ofMillis(verifiedStateExpirationMillis)
        )
    }

    fun verifiedCode(request: AuthCommand.VerifyEmailCode) {
        val (email, authCode) = request
        checkDuplicatedEmail(email)
        val redisAuthCode = emailVerificationRepository.getCode(email)
            ?: throw BusinessException(ErrorCode.AUTH_CODE_NOT_FOUND)

        if (redisAuthCode != authCode) {
            throw BusinessException(ErrorCode.AUTH_CODE_MISMATCH)
        }

        emailVerificationRepository.markVerified(
            email,
            Duration.ofMillis(verifiedStateExpirationMillis)
        )
        emailVerificationRepository.deleteCode(email)
        logger.info("Email verified successfully for Email hashcode: {}", email.hashCode())
    }

    private fun checkDuplicatedEmail(email: String) {
        if (memberRepository.existsByEmail(email)) {
            throw BusinessException(ErrorCode.DUPLICATE_EMAIL)
        }
    }

    private fun createCode(): String {
        return (1..6).joinToString("") {
            Random.nextInt(0, 10).toString()
        }
    }
}
