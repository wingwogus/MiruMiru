package com.example.application.bootstrap

import com.example.domain.member.Member
import com.example.domain.member.MemberRepository
import com.example.domain.university.University
import com.example.domain.university.UniversityRepository
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Profile
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component

@Component
@Profile("local")
class LocalTestDataInitializer(
    private val universityRepository: UniversityRepository,
    private val memberRepository: MemberRepository,
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

        if (memberRepository.existsByEmail(TEST_MEMBER_EMAIL)) {
            return
        }

        memberRepository.save(
            Member(
                university = university,
                email = TEST_MEMBER_EMAIL,
                password = passwordEncoder.encode(TEST_MEMBER_PASSWORD),
                nickname = TEST_MEMBER_NICKNAME,
                role = TEST_MEMBER_ROLE
            )
        )
    }

    companion object {
        private const val TOKYO_UNIVERSITY_NAME = "The University of Tokyo"
        private const val TOKYO_EMAIL_DOMAIN = "tokyo.ac.jp"
        private const val TEST_MEMBER_EMAIL = "test@tokyo.ac.jp"
        private const val TEST_MEMBER_PASSWORD = "password123!"
        private const val TEST_MEMBER_NICKNAME = "test-user"
        private const val TEST_MEMBER_ROLE = "ROLE_USER"
    }
}
