package com.example.domain.lecture

import com.example.domain.course.Course
import com.example.domain.major.Major
import com.example.domain.semester.Semester
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
    name = "lecture",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_lecture_semester_code",
            columnNames = ["semester_id", "code"]
        )
    ],
    indexes = [
        Index(name = "idx_lecture_course", columnList = "course_id")
    ]
)
class Lecture(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "semester_id", nullable = false)
    val semester: Semester,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "major_id")
    val major: Major? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", nullable = false)
    val course: Course,

    @Column(nullable = false, length = 50)
    val code: String,

    @Column(nullable = false)
    val name: String,

    @Column(nullable = false)
    val professor: String,

    @Column(nullable = false)
    val credit: Int
)
