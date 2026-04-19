package com.example.domain.member

import com.example.domain.major.Major
import com.example.domain.university.University
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne

@Entity
class Member(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "university_id", nullable = false)
    val university: University,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "major_id", nullable = false)
    val major: Major,

    @Column(nullable = false, unique = true)
    val email: String,

    @Column(nullable = false)
    val password: String = "",

    @Column(nullable = false, unique = true)
    val nickname: String = "",

    @Column(nullable = false)
    val role: String = "ROLE_USER",
)
