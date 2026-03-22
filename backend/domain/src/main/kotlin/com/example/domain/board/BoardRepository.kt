package com.example.domain.board

import com.example.domain.university.University
import org.springframework.data.jpa.repository.JpaRepository

interface BoardRepository : JpaRepository<Board, Long> {
    fun findByUniversityAndName(university: University, name: String): Board?
}
