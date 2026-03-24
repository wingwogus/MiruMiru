package com.example.domain.course

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
    name = "course",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_course_university_code",
            columnNames = ["university_id", "code"]
        )
    ],
    indexes = [
        Index(name = "idx_course_university_code", columnList = "university_id, code")
    ]
)
class Course(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "university_id", nullable = false)
    val university: University,

    @Column(nullable = false, length = 50)
    val code: String,

    @Column(nullable = false)
    var name: String
) {
    fun rename(name: String) {
        this.name = name
    }
}
