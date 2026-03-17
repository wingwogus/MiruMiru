package com.example.domain.major

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
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "major",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_major_university_code",
            columnNames = ["university_id", "code"]
        )
    ]
)
class Major(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "university_id", nullable = false)
    val university: University,

    @Column(nullable = false, length = 50)
    val code: String,

    @Column(nullable = false)
    val name: String
)
