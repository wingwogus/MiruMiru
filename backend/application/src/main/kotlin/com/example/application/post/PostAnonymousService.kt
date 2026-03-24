package com.example.application.post

import com.example.domain.member.Member
import com.example.domain.post.Post
import com.example.domain.post.PostAnonymousMapping
import com.example.domain.post.PostAnonymousMappingRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class PostAnonymousService(
    private val postAnonymousMappingRepository: PostAnonymousMappingRepository
) {
    fun getOrCreateAnonNumber(post: Post, member: Member): Int {
        postAnonymousMappingRepository.findByPostIdAndMemberId(post.id, member.id)?.let {
            return it.anonNumber
        }

        repeat(3) {
            val nextAnonNumber = (postAnonymousMappingRepository.findTopByPostIdOrderByAnonNumberDesc(post.id)?.anonNumber ?: 0) + 1
            try {
                return postAnonymousMappingRepository.saveAndFlush(
                    PostAnonymousMapping(
                        post = post,
                        member = member,
                        anonNumber = nextAnonNumber
                    )
                ).anonNumber
            } catch (_: DataIntegrityViolationException) {
                // Retry when another transaction claimed the same anon number first.
            }
        }

        return postAnonymousMappingRepository.findByPostIdAndMemberId(post.id, member.id)?.anonNumber
            ?: throw DataIntegrityViolationException("Failed to allocate anonymous number for post=${post.id}, member=${member.id}")
    }

    @Transactional(readOnly = true)
    fun getAnonNumberByMemberId(postId: Long): Map<Long, Int> {
        return postAnonymousMappingRepository.findAllByPostId(postId)
            .associate { mapping -> mapping.member.id to mapping.anonNumber }
    }
}
