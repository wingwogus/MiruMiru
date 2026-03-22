package com.example.domain.semester

import com.example.domain.university.University
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
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
    name = "semester",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_semester_university_year_term",
            columnNames = ["university_id", "academic_year", "term"]
        )
    ]
)
class Semester(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "university_id", nullable = false)
    val university: University,

    @Column(name = "academic_year", nullable = false)
    val academicYear: Int,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val term: SemesterTerm
)
