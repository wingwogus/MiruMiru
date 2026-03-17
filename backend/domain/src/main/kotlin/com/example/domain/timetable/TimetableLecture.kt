package com.example.domain.timetable

import com.example.domain.lecture.Lecture
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
    name = "timetable_lecture",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_timetable_lecture_pair",
            columnNames = ["timetable_id", "lecture_id"]
        )
    ]
)
class TimetableLecture(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "timetable_id", nullable = false)
    val timetable: Timetable,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lecture_id", nullable = false)
    val lecture: Lecture
)
