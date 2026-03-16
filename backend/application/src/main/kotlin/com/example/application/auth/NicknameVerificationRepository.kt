package com.example.application.auth

import java.time.Duration

interface NicknameVerificationRepository {
    fun markVerified(nickname: String, ttl: Duration)

    fun isVerified(nickname: String): Boolean

    fun delete(nickname: String)
}
