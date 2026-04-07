package com.example.application.redis

import java.time.Duration

interface NicknameVerificationRepository {
    fun markVerified(nickname: String, ttl: Duration)

    fun isVerified(nickname: String): Boolean

    fun delete(nickname: String)
}