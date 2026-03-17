package com.example.domain.member

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import com.example.domain.university.University

@Entity
class Member(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "university_id", nullable = false)
    val university: University,

    @Column(nullable = false, unique = true)
    val email: String,

    @Column(nullable = false)
    val password: String = "",

    @Column(nullable = false, unique = true)
    val nickname: String = "",

    @Column(nullable = false)
    val role: String = "ROLE_USER",
)
