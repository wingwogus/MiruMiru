package com.example.application.bootstrap

import com.example.domain.lecture.Lecture
import com.example.domain.lecture.LectureRepository
import com.example.domain.lecture.LectureSchedule
import com.example.domain.lecture.LectureScheduleRepository
import com.example.domain.major.Major
import com.example.domain.major.MajorRepository
import com.example.domain.member.Member
import com.example.domain.member.MemberRepository
import com.example.domain.semester.Semester
import com.example.domain.semester.SemesterRepository
import com.example.domain.semester.SemesterTerm
import com.example.domain.timetable.Timetable
import com.example.domain.timetable.TimetableLecture
import com.example.domain.timetable.TimetableLectureRepository
import com.example.domain.timetable.TimetableRepository
import com.example.domain.university.University
import com.example.domain.university.UniversityRepository
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Profile
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.DayOfWeek
import java.time.LocalTime

@Component
@Profile("local")
@Transactional
class LocalTestDataInitializer(
    private val universityRepository: UniversityRepository,
    private val majorRepository: MajorRepository,
    private val memberRepository: MemberRepository,
    private val semesterRepository: SemesterRepository,
    private val lectureRepository: LectureRepository,
    private val lectureScheduleRepository: LectureScheduleRepository,
    private val timetableRepository: TimetableRepository,
    private val timetableLectureRepository: TimetableLectureRepository,
    private val passwordEncoder: PasswordEncoder
) : ApplicationRunner {

    override fun run(args: ApplicationArguments) {
        val university = findOrCreateUniversity()
        val majorsByCode = MAJOR_SEEDS.associate { seed ->
            seed.code to findOrCreateMajor(university, seed)
        }
        val member = findOrCreateMember(university, majorsByCode.getValue(TEST_MEMBER_MAJOR_CODE))
        findOrCreateMember(university, majorsByCode.getValue(EMPTY_MEMBER_MAJOR_CODE), EMPTY_MEMBER_EMAIL, EMPTY_MEMBER_NICKNAME)
        val semester = findOrCreateSemester(university)
        val lectures = LECTURE_SEEDS.map { seed ->
            findOrCreateLecture(semester, majorsByCode[seed.majorCode], seed)
        }

        lectures.zip(LECTURE_SEEDS).forEach { (lecture, seed) ->
            seed.schedules.forEach { schedule ->
                findOrCreateLectureSchedule(lecture, schedule)
            }
        }

        val timetable = findOrCreateTimetable(member, semester)
        lectures.filter { INITIAL_TIMETABLE_LECTURE_CODES.contains(it.code) }.forEach { lecture ->
            linkLectureToTimetable(timetable, lecture)
        }
    }

    private fun findOrCreateUniversity(): University {
        return universityRepository.findByEmailDomain(TOKYO_EMAIL_DOMAIN)
            ?: universityRepository.save(
                University(
                    name = TOKYO_UNIVERSITY_NAME,
                    emailDomain = TOKYO_EMAIL_DOMAIN
                )
            )
    }

    private fun findOrCreateMajor(university: University, seed: MajorSeed): Major {
        return majorRepository.findByUniversityIdAndCode(university.id, seed.code)
            ?: majorRepository.save(
                Major(
                    university = university,
                    code = seed.code,
                    name = seed.name
                )
            )
    }

    private fun findOrCreateMember(
        university: University,
        major: Major,
        email: String = TEST_MEMBER_EMAIL,
        nickname: String = TEST_MEMBER_NICKNAME
    ): Member {
        val existingMember = memberRepository.findByEmail(email)
        if (existingMember != null) {
            require(existingMember.university.id == university.id) {
                "Test member must belong to the seeded university."
            }
            require(existingMember.major.id == major.id) {
                "Test member must belong to the seeded major."
            }
            return existingMember
        }

        return memberRepository.save(
            Member(
                university = university,
                major = major,
                email = email,
                password = passwordEncoder.encode(TEST_MEMBER_PASSWORD),
                nickname = nickname,
                role = TEST_MEMBER_ROLE
            )
        )
    }

    private fun findOrCreateSemester(university: University): Semester {
        return semesterRepository.findByUniversityIdAndAcademicYearAndTerm(
            universityId = university.id,
            academicYear = SEED_ACADEMIC_YEAR,
            term = SEED_TERM
        ) ?: semesterRepository.save(
            Semester(
                university = university,
                academicYear = SEED_ACADEMIC_YEAR,
                term = SEED_TERM
            )
            )
    }

    private fun findOrCreateLecture(semester: Semester, major: Major?, seed: LectureSeed): Lecture {
        return lectureRepository.findBySemesterIdAndCode(semester.id, seed.code)
            ?: lectureRepository.save(
                Lecture(
                    semester = semester,
                    major = major,
                    code = seed.code,
                    name = seed.name,
                    professor = seed.professor,
                    credit = seed.credit
                )
            )
    }

    private fun findOrCreateLectureSchedule(lecture: Lecture, seed: LectureScheduleSeed): LectureSchedule {
        return lectureScheduleRepository.findByLectureIdAndDayOfWeekAndStartTimeAndEndTime(
            lectureId = lecture.id,
            dayOfWeek = seed.dayOfWeek,
            startTime = seed.startTime,
            endTime = seed.endTime
        ) ?: lectureScheduleRepository.save(
            LectureSchedule(
                lecture = lecture,
                dayOfWeek = seed.dayOfWeek,
                startTime = seed.startTime,
                endTime = seed.endTime,
                location = seed.location
            )
        )
    }

    private fun findOrCreateTimetable(member: Member, semester: Semester): Timetable {
        require(member.university.id == semester.university.id) {
            "Timetable member and semester must belong to the same university."
        }

        return timetableRepository.findByMemberIdAndSemesterId(member.id, semester.id)
            ?: timetableRepository.save(
                Timetable(
                    member = member,
                    semester = semester
                )
            )
    }

    private fun linkLectureToTimetable(timetable: Timetable, lecture: Lecture) {
        require(timetable.semester.id == lecture.semester.id) {
            "Timetable and lecture must belong to the same semester."
        }
        require(timetable.member.university.id == lecture.semester.university.id) {
            "Timetable member and lecture must belong to the same university."
        }

        if (timetableLectureRepository.existsByTimetableIdAndLectureId(timetable.id, lecture.id)) {
            return
        }

        timetableLectureRepository.save(
            TimetableLecture(
                timetable = timetable,
                lecture = lecture
            )
        )
    }

    companion object {
        private const val TOKYO_UNIVERSITY_NAME = "The University of Tokyo"
        private const val TOKYO_EMAIL_DOMAIN = "tokyo.ac.jp"
        private const val TEST_MEMBER_EMAIL = "test@tokyo.ac.jp"
        private const val TEST_MEMBER_PASSWORD = "password123!"
        private const val TEST_MEMBER_NICKNAME = "test-user"
        private const val TEST_MEMBER_ROLE = "ROLE_USER"
        private const val TEST_MEMBER_MAJOR_CODE = "CS"
        private const val EMPTY_MEMBER_EMAIL = "empty@tokyo.ac.jp"
        private const val EMPTY_MEMBER_NICKNAME = "empty-user"
        private const val EMPTY_MEMBER_MAJOR_CODE = "MATH"
        private const val SEED_ACADEMIC_YEAR = 2026
        private val SEED_TERM = SemesterTerm.SPRING
        private val INITIAL_TIMETABLE_LECTURE_CODES = setOf("CS101", "MATH201")

        private val MAJOR_SEEDS = listOf(
            MajorSeed(
                code = "CS",
                name = "Computer Science"
            ),
            MajorSeed(
                code = "MATH",
                name = "Mathematics"
            )
        )

        private val LECTURE_SEEDS = listOf(
            LectureSeed(
                code = "CS101",
                name = "Introduction to Computer Science",
                professor = "Prof. Akiyama",
                credit = 3,
                majorCode = "CS",
                schedules = listOf(
                    LectureScheduleSeed(
                        dayOfWeek = DayOfWeek.MONDAY,
                        startTime = LocalTime.of(9, 0),
                        endTime = LocalTime.of(10, 30),
                        location = "Engineering Hall 101"
                    ),
                    LectureScheduleSeed(
                        dayOfWeek = DayOfWeek.WEDNESDAY,
                        startTime = LocalTime.of(9, 0),
                        endTime = LocalTime.of(10, 30),
                        location = "Engineering Hall 101"
                    )
                )
            ),
            LectureSeed(
                code = "MATH201",
                name = "Linear Algebra",
                professor = "Prof. Sato",
                credit = 2,
                majorCode = null,
                schedules = listOf(
                    LectureScheduleSeed(
                        dayOfWeek = DayOfWeek.TUESDAY,
                        startTime = LocalTime.of(13, 0),
                        endTime = LocalTime.of(14, 30),
                        location = "Science Building 202"
                    )
                )
            ),
            LectureSeed(
                code = "PHYS301",
                name = "Classical Mechanics",
                professor = "Prof. Tanaka",
                credit = 3,
                majorCode = null,
                schedules = listOf(
                    LectureScheduleSeed(
                        dayOfWeek = DayOfWeek.MONDAY,
                        startTime = LocalTime.of(10, 0),
                        endTime = LocalTime.of(11, 0),
                        location = "Physics Hall 303"
                    )
                )
            ),
            LectureSeed(
                code = "HIST110",
                name = "World History",
                professor = "Prof. Nakamura",
                credit = 2,
                majorCode = null,
                schedules = listOf(
                    LectureScheduleSeed(
                        dayOfWeek = DayOfWeek.TUESDAY,
                        startTime = LocalTime.of(9, 0),
                        endTime = LocalTime.of(10, 30),
                        location = "Humanities Hall 105"
                    )
                )
            ),
            LectureSeed(
                code = "CHEM105",
                name = "General Chemistry",
                professor = "Prof. Suzuki",
                credit = 3,
                majorCode = null,
                schedules = listOf(
                    LectureScheduleSeed(
                        dayOfWeek = DayOfWeek.WEDNESDAY,
                        startTime = LocalTime.of(14, 0),
                        endTime = LocalTime.of(15, 30),
                        location = "Science Building 110"
                    )
                )
            ),
            LectureSeed(
                code = "ECON210",
                name = "Microeconomics",
                professor = "Prof. Kobayashi",
                credit = 2,
                majorCode = null,
                schedules = listOf(
                    LectureScheduleSeed(
                        dayOfWeek = DayOfWeek.THURSDAY,
                        startTime = LocalTime.of(10, 0),
                        endTime = LocalTime.of(11, 30),
                        location = "Social Sciences 204"
                    )
                )
            ),
            LectureSeed(
                code = "ENG220",
                name = "Academic English",
                professor = "Prof. Wilson",
                credit = 2,
                majorCode = null,
                schedules = listOf(
                    LectureScheduleSeed(
                        dayOfWeek = DayOfWeek.THURSDAY,
                        startTime = LocalTime.of(13, 0),
                        endTime = LocalTime.of(14, 30),
                        location = "Language Center 301"
                    )
                )
            ),
            LectureSeed(
                code = "STAT230",
                name = "Statistics Fundamentals",
                professor = "Prof. Yamamoto",
                credit = 2,
                majorCode = "MATH",
                schedules = listOf(
                    LectureScheduleSeed(
                        dayOfWeek = DayOfWeek.FRIDAY,
                        startTime = LocalTime.of(9, 0),
                        endTime = LocalTime.of(10, 30),
                        location = "Science Building 305"
                    )
                )
            ),
            LectureSeed(
                code = "ART150",
                name = "Visual Arts Appreciation",
                professor = "Prof. Lee",
                credit = 2,
                majorCode = null,
                schedules = listOf(
                    LectureScheduleSeed(
                        dayOfWeek = DayOfWeek.FRIDAY,
                        startTime = LocalTime.of(11, 0),
                        endTime = LocalTime.of(12, 30),
                        location = "Arts Hall 201"
                    )
                )
            )
        )
    }

    private data class MajorSeed(
        val code: String,
        val name: String
    )

    private data class LectureSeed(
        val code: String,
        val name: String,
        val professor: String,
        val credit: Int,
        val majorCode: String?,
        val schedules: List<LectureScheduleSeed>
    )

    private data class LectureScheduleSeed(
        val dayOfWeek: DayOfWeek,
        val startTime: LocalTime,
        val endTime: LocalTime,
        val location: String
    )
}
