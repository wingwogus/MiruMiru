package com.example.api.timetable

import com.example.ApiApplication
import com.example.domain.lecture.Lecture
import com.example.domain.lecture.LectureRepository
import com.example.domain.lecture.LectureScheduleRepository
import com.example.domain.member.MemberRepository
import com.example.domain.semester.SemesterRepository
import com.example.domain.semester.SemesterTerm
import com.example.domain.timetable.Timetable
import com.example.domain.timetable.TimetableLecture
import com.example.domain.timetable.TimetableLectureRepository
import com.example.domain.timetable.TimetableRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.time.DayOfWeek

@SpringBootTest(
    classes = [ApiApplication::class],
    properties = [
        "spring.mail.username=test@example.com",
        "spring.mail.password=test-password"
    ]
)
@ActiveProfiles("local")
@Transactional
class TimetablePersistenceIntegrationTest(
    @Autowired private val memberRepository: MemberRepository,
    @Autowired private val semesterRepository: SemesterRepository,
    @Autowired private val lectureRepository: LectureRepository,
    @Autowired private val lectureScheduleRepository: LectureScheduleRepository,
    @Autowired private val timetableRepository: TimetableRepository,
    @Autowired private val timetableLectureRepository: TimetableLectureRepository
) {

    @Test
    fun `local bootstrap seeds semester lecture schedule and timetable graph`() {
        val member = memberRepository.findByEmail("test@tokyo.ac.jp")
        assertNotNull(member)
        assertEquals("Computer Science", member!!.major.name)

        val semester = semesterRepository.findByUniversityIdAndAcademicYearAndTerm(
            universityId = member.university.id,
            academicYear = 2026,
            term = SemesterTerm.SPRING
        )
        assertNotNull(semester)

        val lecture = lectureRepository.findBySemesterIdAndCode(semester!!.id, "CS101")
        assertNotNull(lecture)
        assertEquals("Computer Science", lecture!!.major?.name)

        val generalLecture = lectureRepository.findBySemesterIdAndCode(semester.id, "MATH201")
        assertNotNull(generalLecture)
        assertEquals(null, generalLecture!!.major)
        val conflictLecture = lectureRepository.findBySemesterIdAndCode(semester.id, "PHYS301")
        assertNotNull(conflictLecture)
        assertEquals(null, conflictLecture!!.major)

        val lectureSchedule = lectureScheduleRepository.findByLectureIdAndDayOfWeekAndStartTimeAndEndTime(
            lectureId = lecture.id,
            dayOfWeek = java.time.DayOfWeek.MONDAY,
            startTime = java.time.LocalTime.of(9, 0),
            endTime = java.time.LocalTime.of(10, 30)
        )
        assertNotNull(lectureSchedule)

        val dayCounts = lectureScheduleRepository.findAllByLectureIdIn(
            lectureRepository.findAllBySemesterIdOrderByCodeAsc(semester.id).map(Lecture::id)
        ).groupingBy { it.dayOfWeek }
            .eachCount()
        assertEquals(2, dayCounts[DayOfWeek.MONDAY])
        assertEquals(2, dayCounts[DayOfWeek.TUESDAY])
        assertEquals(2, dayCounts[DayOfWeek.WEDNESDAY])
        assertEquals(2, dayCounts[DayOfWeek.THURSDAY])
        assertEquals(2, dayCounts[DayOfWeek.FRIDAY])

        val timetable = timetableRepository.findByMemberIdAndSemesterId(member.id, semester.id)
        assertNotNull(timetable)
        assertEquals(true, timetableLectureRepository.existsByTimetableIdAndLectureId(timetable!!.id, lecture.id))
        assertEquals(true, timetableLectureRepository.existsByTimetableIdAndLectureId(timetable.id, generalLecture.id))
        assertEquals(false, timetableLectureRepository.existsByTimetableIdAndLectureId(timetable.id, conflictLecture.id))
    }

    @Test
    fun `local bootstrap keeps second user without timetable for empty-state tests`() {
        val member = memberRepository.findByEmail("empty@tokyo.ac.jp")
        assertNotNull(member)

        val semester = semesterRepository.findByUniversityIdAndAcademicYearAndTerm(
            universityId = member!!.university.id,
            academicYear = 2026,
            term = SemesterTerm.SPRING
        )
        assertNotNull(semester)
        assertEquals(null, timetableRepository.findByMemberIdAndSemesterId(member.id, semester!!.id))
    }

    @Test
    fun `unique constraints prevent duplicate timetable and lecture links`() {
        val member = memberRepository.findByEmail("test@tokyo.ac.jp")!!
        val semester = semesterRepository.findByUniversityIdAndAcademicYearAndTerm(
            universityId = member.university.id,
            academicYear = 2026,
            term = SemesterTerm.SPRING
        )!!
        val lecture = lectureRepository.findBySemesterIdAndCode(semester.id, "CS101")!!
        val timetable = timetableRepository.findByMemberIdAndSemesterId(member.id, semester.id)!!

        assertThrows<DataIntegrityViolationException> {
            timetableRepository.saveAndFlush(
                Timetable(
                    member = member,
                    semester = semester
                )
            )
        }

        assertThrows<DataIntegrityViolationException> {
            timetableLectureRepository.saveAndFlush(
                TimetableLecture(
                    timetable = timetable,
                    lecture = lecture
                )
            )
        }
    }
}
