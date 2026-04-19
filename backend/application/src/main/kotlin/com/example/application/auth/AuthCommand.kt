package com.example.application.auth

object AuthCommand {
    data class Login(
        val email: String,
        val password: String
    )

    data class Reissue(
        val accessToken: String,
        val refreshToken: String
    )

    data class SignUp(
        val email: String,
        val password: String,
        val nickname: String,
        val majorId: Long
    )

    data class VerifyEmailCode(
        val email: String,
        val code: String
    )

    data class VerifyNickname(
        val nickname: String
    )
}
