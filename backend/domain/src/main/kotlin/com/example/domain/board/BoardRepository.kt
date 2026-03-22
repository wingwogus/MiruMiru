package com.example.domain.board

import com.example.domain.university.University
import org.springframework.data.jpa.repository.JpaRepository

interface BoardRepository : JpaRepository<Board, Long> {
    fun findByUniversityAndName(university: University, name: String): Board?
    fun findAllByUniversityIdOrderByIdAsc(universityId: Long): List<Board>
    fun findByIdAndUniversityId(id: Long, universityId: Long): Board?
    fun findByUniversityIdAndCode(universityId: Long, code: String): Board?
}
