package com.example.application.bootstrap

import com.example.domain.board.Board
import com.example.domain.board.BoardRepository
import com.example.domain.member.Member
import com.example.domain.member.MemberRepository
import com.example.domain.post.Post
import com.example.domain.post.PostRepository
import com.example.domain.university.University
import com.example.domain.university.UniversityRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Profile
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component

@Component
@Profile("local", "docker")
class LocalTestDataInitializer(
    private val universityRepository: UniversityRepository,
    private val memberRepository: MemberRepository,
    private val boardRepository: BoardRepository,
    private val postRepository: PostRepository,
    private val passwordEncoder: PasswordEncoder
) : ApplicationRunner {

    override fun run(args: ApplicationArguments) {
        val university = universityRepository.findByEmailDomain(TOKYO_EMAIL_DOMAIN)
            ?: universityRepository.save(
                University(
                    name = TOKYO_UNIVERSITY_NAME,
                    emailDomain = TOKYO_EMAIL_DOMAIN
                )
            )

        val member1 = memberRepository.findByEmail(TEST_MEMBER_EMAIL)
            ?: memberRepository.save(
                Member(
                    university = university,
                    email = TEST_MEMBER_EMAIL,
                    password = passwordEncoder.encode(TEST_MEMBER_PASSWORD),
                    nickname = TEST_MEMBER_NICKNAME,
                    role = TEST_MEMBER_ROLE
                )
            )

        val member2 = memberRepository.findByEmail(TEST_MEMBER2_EMAIL)
            ?: memberRepository.save(
                Member(
                    university = university,
                    email = TEST_MEMBER2_EMAIL,
                    password = passwordEncoder.encode(TEST_MEMBER2_PASSWORD),
                    nickname = TEST_MEMBER2_NICKNAME,
                    role = TEST_MEMBER2_ROLE
                )
            )

        val board = boardRepository.findByUniversityAndName(university, TEST_BOARD_NAME)
            ?: boardRepository.save(
                Board(
                    university = university,
                    category = TEST_BOARD_CATEGORY,
                    name = TEST_BOARD_NAME,
                    isAnonymousAllowed = true,
                )
            )

        val post = postRepository.findByTitle(TEST_POST_TITLE)
            ?: postRepository.save(
                Post(
                    board = board,
                    member = member2,
                    title = TEST_POST_TITLE,
                    content = TEST_POST_CONTENT,
                    isAnonymous = TEST_POST_IS_ANONYMOUS,
                )
            )

        log.info(
            "Local seed ready. member1(email={}, pw={}, id={}), member2(email={}, pw={}, id={}), post(id={}, title={})",
            TEST_MEMBER_EMAIL,
            TEST_MEMBER_PASSWORD,
            member1.id,
            TEST_MEMBER2_EMAIL,
            TEST_MEMBER2_PASSWORD,
            member2.id,
            post.id,
            post.title,
        )
    }

    companion object {
        private val log = LoggerFactory.getLogger(LocalTestDataInitializer::class.java)

        private const val TOKYO_UNIVERSITY_NAME = "The University of Tokyo"
        private const val TOKYO_EMAIL_DOMAIN = "tokyo.ac.jp"

        private const val TEST_MEMBER_EMAIL = "test@tokyo.ac.jp"
        private const val TEST_MEMBER_PASSWORD = "password123!"
        private const val TEST_MEMBER_NICKNAME = "test-user"
        private const val TEST_MEMBER_ROLE = "ROLE_USER"

        private const val TEST_MEMBER2_EMAIL = "test2@tokyo.ac.jp"
        private const val TEST_MEMBER2_PASSWORD = "password123!"
        private const val TEST_MEMBER2_NICKNAME = "test-user-2"
        private const val TEST_MEMBER2_ROLE = "ROLE_USER"

        private const val TEST_BOARD_CATEGORY = "COMMUNITY"
        private const val TEST_BOARD_NAME = "Local Free Board"

        private const val TEST_POST_TITLE = "[LOCAL] Chat Seed Post"
        private const val TEST_POST_CONTENT = "This is a local seed post for creating 1:1 chat rooms."
        private const val TEST_POST_IS_ANONYMOUS = true
    }
}
