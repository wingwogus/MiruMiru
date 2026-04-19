package com.example.domain.board

import org.springframework.data.jpa.repository.JpaRepository

interface BoardRepository : JpaRepository<Board, Long> {
    fun findAllByUniversityIdOrderByIdAsc(universityId: Long): List<Board>
    fun findByIdAndUniversityId(id: Long, universityId: Long): Board?
    fun findByUniversityIdAndCode(universityId: Long, code: String): Board?
}
