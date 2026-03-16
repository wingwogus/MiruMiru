package com.example.application.redis

interface RefreshTokenRepository {
    fun save(userId: Long, refreshToken: String, expiresInSeconds: Long)

    fun get(userId: Long): String?

    fun delete(userId: Long)
}