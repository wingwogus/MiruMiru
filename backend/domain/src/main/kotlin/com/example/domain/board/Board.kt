package com.example.domain.board

import com.example.domain.university.University
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "board",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_board_university_code",
            columnNames = ["university_id", "code"]
        )
    ],
    indexes = [
        Index(name = "idx_board_university_code", columnList = "university_id, code")
    ]
)
class Board(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "university_id", nullable = false)
    val university: University,

    @Column(nullable = false, length = 100)
    val code: String,

    @Column(nullable = false, length = 100)
    val name: String,

    @Column(name = "is_anonymous_allowed", nullable = false)
    val isAnonymousAllowed: Boolean
)
