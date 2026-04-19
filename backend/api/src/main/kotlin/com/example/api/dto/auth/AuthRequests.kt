package com.example.api.dto.auth

import com.example.api.validation.annotation.JapaneseUniversityEmail
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

object AuthRequests {
    data class EmailSendCodeRequest(
        @field:NotBlank(message = "이메일을 입력해주세요")
        @field:Email(message = "이메일 형식이 올바르지 않습니다")
        @field:JapaneseUniversityEmail
        val email: String
    )

    data class EmailVerifyCodeRequest(
        @field:NotBlank(message = "이메일을 입력해주세요")
        @field:Email(message = "이메일 형식이 올바르지 않습니다")
        @field:JapaneseUniversityEmail
        val email: String,
        @field:NotBlank(message = "인증번호를 입력해주세요")
        @field:Size(min = 6, max = 6, message = "인증번호는 6자리여야 합니다")
        val code: String
    )

    data class NicknameVerifyRequest(
        @field:NotBlank(message = "닉네임을 입력해주세요")
        val nickname: String
    )

    data class SignUpRequest(
        @field:NotBlank(message = "이메일을 입력해주세요")
        @field:Email(message = "이메일 형식이 올바르지 않습니다")
        @field:JapaneseUniversityEmail
        val email: String,
        @field:NotBlank(message = "비밀번호를 입력해주세요")
        val password: String,
        @field:NotBlank(message = "닉네임을 입력해주세요")
        val nickname: String,
        @field:NotNull(message = "전공을 선택해주세요")
        val majorId: Long?,
        val avatar: String? = null
    )

    data class MajorListRequest(
        @field:NotBlank(message = "이메일을 입력해주세요")
        @field:Email(message = "이메일 형식이 올바르지 않습니다")
        @field:JapaneseUniversityEmail
        val email: String
    )

    data class LoginRequest(
        @field:NotBlank(message = "이메일을 입력해주세요")
        @field:Email(message = "이메일 형식이 올바르지 않습니다")
        @field:JapaneseUniversityEmail
        val email: String,
        @field:NotBlank(message = "비밀번호를 입력해주세요")
        val password: String
    )

    data class ReissueRequest(
        @field:NotBlank(message = "AccessToken을 입력해주세요")
        val accessToken: String,
        @field:NotBlank(message = "RefreshToken을 입력해주세요")
        val refreshToken: String
    )
}
