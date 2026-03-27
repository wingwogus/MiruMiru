package com.example.domain.course

import org.springframework.data.jpa.repository.JpaRepository

interface CourseRepository : JpaRepository<Course, Long> {
    fun findByIdAndUniversityId(id: Long, universityId: Long): Course?
    fun findByUniversityIdAndCode(universityId: Long, code: String): Course?
}
