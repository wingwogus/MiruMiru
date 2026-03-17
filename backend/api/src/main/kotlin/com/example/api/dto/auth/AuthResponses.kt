package com.example.api.dto.auth

import com.example.application.auth.AuthResult

object AuthResponses {
    data class TokenResponse(
        val accessToken: String,
        val refreshToken: String
    ) {
        companion object {
            fun from(result: AuthResult.TokenPair): TokenResponse {
                return TokenResponse(
                    accessToken = result.accessToken,
                    refreshToken = result.refreshToken
                )
            }
        }
    }

    data class MajorOptionResponse(
        val majorId: Long,
        val code: String,
        val name: String
    ) {
        companion object {
            fun from(result: AuthResult.MajorOption): MajorOptionResponse {
                return MajorOptionResponse(
                    majorId = result.majorId,
                    code = result.code,
                    name = result.name
                )
            }
        }
    }
}
