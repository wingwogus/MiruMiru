package com.example.application.redis

import com.example.application.auth.NicknameVerificationRepository
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Repository
import java.time.Duration

@Repository
class NicknameVerificationRedisRepository(
    private val redis: StringRedisTemplate
) : NicknameVerificationRepository {

    companion object {
        private const val PREFIX = "verified-nickname:"
    }

    override fun markVerified(nickname: String, ttl: Duration) {
        redis.opsForValue().set(PREFIX + nickname, "true", ttl)
    }

    override fun isVerified(nickname: String): Boolean {
        return redis.opsForValue().get(PREFIX + nickname) == "true"
    }

    override fun delete(nickname: String) {
        redis.delete(PREFIX + nickname)
    }
}
