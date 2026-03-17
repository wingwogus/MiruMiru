package com.example.application.auth

object AuthResult {
    data class TokenPair(
        val accessToken: String,
        val refreshToken: String
    )

    data class MajorOption(
        val majorId: Long,
        val code: String,
        val name: String
    )
}
