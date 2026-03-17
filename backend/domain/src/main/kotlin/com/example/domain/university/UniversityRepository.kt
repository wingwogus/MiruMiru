package com.example.domain.university

import org.springframework.data.jpa.repository.JpaRepository

interface UniversityRepository : JpaRepository<University, Long> {
    fun findByEmailDomain(emailDomain: String): University?
    fun existsByEmailDomain(emailDomain: String): Boolean
}
