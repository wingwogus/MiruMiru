package com.example.domain.major

import org.springframework.data.jpa.repository.JpaRepository

interface MajorRepository : JpaRepository<Major, Long> {
    fun findAllByUniversityIdOrderByNameAsc(universityId: Long): List<Major>
    fun findByIdAndUniversityId(id: Long, universityId: Long): Major?
    fun findByUniversityIdAndCode(universityId: Long, code: String): Major?
}
