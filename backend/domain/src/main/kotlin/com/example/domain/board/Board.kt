package com.example.domain.board

import com.example.domain.common.AuditableEntity
import com.example.domain.university.University
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

@Entity
@Table(name = "board")
class Board(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "university_id", nullable = false)
    val university: University,

    @Column(nullable = false)
    val category: String,

    @Column(nullable = false)
    val name: String,

    @Column(name = "is_anonymous_allowed", nullable = false)
    val isAnonymousAllowed: Boolean = false,
) : AuditableEntity()

