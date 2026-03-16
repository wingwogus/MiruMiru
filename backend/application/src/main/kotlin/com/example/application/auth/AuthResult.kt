package com.example.application.auth

object AuthResult {
    data class TokenPair(
        val accessToken: String,
        val refreshToken: String
    )
}
