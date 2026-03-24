package com.example.application.bootstrap

import com.example.domain.board.Board
import com.example.domain.board.BoardRepository
import com.example.domain.comment.Comment
import com.example.domain.comment.CommentRepository
import com.example.domain.course.Course
import com.example.domain.course.CourseRepository
import com.example.domain.course.CourseReview
import com.example.domain.course.CourseReviewRepository
import com.example.domain.lecture.Lecture
import com.example.domain.lecture.LectureRepository
import com.example.domain.lecture.LectureSchedule
import com.example.domain.lecture.LectureScheduleRepository
import com.example.domain.major.Major
import com.example.domain.major.MajorRepository
import com.example.domain.member.Member
import com.example.domain.member.MemberRepository
import com.example.domain.post.Post
import com.example.domain.post.PostAnonymousMapping
import com.example.domain.post.PostAnonymousMappingRepository
import com.example.domain.post.PostImage
import com.example.domain.post.PostImageRepository
import com.example.domain.post.PostLike
import com.example.domain.post.PostLikeRepository
import com.example.domain.post.PostRepository
import com.example.domain.semester.Semester
import com.example.domain.semester.SemesterRepository
import com.example.domain.semester.SemesterTerm
import com.example.domain.timetable.Timetable
import com.example.domain.timetable.TimetableLecture
import com.example.domain.timetable.TimetableLectureRepository
import com.example.domain.timetable.TimetableRepository
import com.example.domain.university.University
import com.example.domain.university.UniversityRepository
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.boot.DefaultApplicationArguments
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.DayOfWeek
import java.time.LocalTime

class LocalTestDataInitializerTest {
    private val universityRepository = mock(UniversityRepository::class.java)
    private val majorRepository = mock(MajorRepository::class.java)
    private val memberRepository = mock(MemberRepository::class.java)
    private val boardRepository = mock(BoardRepository::class.java)
    private val postRepository = mock(PostRepository::class.java)
    private val postLikeRepository = mock(PostLikeRepository::class.java)
    private val postAnonymousMappingRepository = mock(PostAnonymousMappingRepository::class.java)
    private val commentRepository = mock(CommentRepository::class.java)
    private val postImageRepository = mock(PostImageRepository::class.java)
    private val semesterRepository = mock(SemesterRepository::class.java)
    private val courseRepository = mock(CourseRepository::class.java)
    private val courseReviewRepository = mock(CourseReviewRepository::class.java)
    private val lectureRepository = mock(LectureRepository::class.java)
    private val lectureScheduleRepository = mock(LectureScheduleRepository::class.java)
    private val timetableRepository = mock(TimetableRepository::class.java)
    private val timetableLectureRepository = mock(TimetableLectureRepository::class.java)
    private val passwordEncoder = mock(PasswordEncoder::class.java)
    private val initializer = LocalTestDataInitializer(
        universityRepository = universityRepository,
        majorRepository = majorRepository,
        memberRepository = memberRepository,
        boardRepository = boardRepository,
        postRepository = postRepository,
        postLikeRepository = postLikeRepository,
        postAnonymousMappingRepository = postAnonymousMappingRepository,
        commentRepository = commentRepository,
        postImageRepository = postImageRepository,
        semesterRepository = semesterRepository,
        courseRepository = courseRepository,
        courseReviewRepository = courseReviewRepository,
        lectureRepository = lectureRepository,
        lectureScheduleRepository = lectureScheduleRepository,
        timetableRepository = timetableRepository,
        timetableLectureRepository = timetableLectureRepository,
        passwordEncoder = passwordEncoder
    )

    @Test
    fun `creates missing seed graph including course reviews`() {
        val university = university()
        val computerScience = major(id = 10L, university = university, code = "CS", name = "Computer Science")
        val mathematics = major(id = 11L, university = university, code = "MATH", name = "Mathematics")
        val testMember = member(id = 2L, university = university, major = computerScience, email = "test@tokyo.ac.jp", nickname = "test-user")
        val emptyMember = member(id = 3L, university = university, major = mathematics, email = "empty@tokyo.ac.jp", nickname = "empty-user")
        val generalBoard = board(id = 20L, university = university, code = "general", name = "General", isAnonymousAllowed = false)
        val freeBoard = board(id = 21L, university = university, code = "free", name = "Free Talk", isAnonymousAllowed = true)
        val springSemester = semester(id = 4L, university = university, academicYear = 2026, term = SemesterTerm.SPRING)
        val fallSemester = semester(id = 5L, university = university, academicYear = 2025, term = SemesterTerm.FALL)
        val courses = courses(university)
        val lectures = lectures(springSemester, fallSemester, computerScience, mathematics, courses)
        val timetable = Timetable(id = 40L, member = testMember, semester = springSemester)
        val generalPost = post(id = 50L, board = generalBoard, member = testMember, title = "Welcome to MiruMiru", isAnonymous = false, likeCount = 0, commentCount = 0)
        val freePost = post(id = 51L, board = freeBoard, member = testMember, title = "Best lunch near campus?", isAnonymous = true, likeCount = 1, commentCount = 2)
        val rootComment = Comment(id = 70L, post = freePost, member = emptyMember, parent = null, content = "There is a cheap curry place behind the engineering building.", isAnonymous = true, isDeleted = false)
        val childComment = Comment(id = 71L, post = freePost, member = testMember, parent = rootComment, content = "Thanks, I will try that place tomorrow.", isAnonymous = true, isDeleted = false)

        `when`(universityRepository.findByEmailDomain("tokyo.ac.jp")).thenReturn(null)
        `when`(universityRepository.save(any(University::class.java))).thenReturn(university)

        `when`(majorRepository.findByUniversityIdAndCode(1L, "CS")).thenReturn(null)
        `when`(majorRepository.findByUniversityIdAndCode(1L, "MATH")).thenReturn(null)
        `when`(majorRepository.save(any(Major::class.java))).thenReturn(computerScience).thenReturn(mathematics)

        `when`(memberRepository.findByEmail("test@tokyo.ac.jp")).thenReturn(null)
        `when`(memberRepository.findByEmail("empty@tokyo.ac.jp")).thenReturn(null)
        `when`(passwordEncoder.encode("password123!")).thenReturn("encoded-password")
        `when`(memberRepository.save(any(Member::class.java))).thenReturn(testMember).thenReturn(emptyMember)

        `when`(boardRepository.findByUniversityIdAndCode(1L, "general")).thenReturn(null)
        `when`(boardRepository.findByUniversityIdAndCode(1L, "free")).thenReturn(null)
        `when`(boardRepository.save(any(Board::class.java))).thenReturn(generalBoard).thenReturn(freeBoard)

        `when`(semesterRepository.findByUniversityIdAndAcademicYearAndTerm(1L, 2026, SemesterTerm.SPRING)).thenReturn(null)
        `when`(semesterRepository.findByUniversityIdAndAcademicYearAndTerm(1L, 2025, SemesterTerm.FALL)).thenReturn(null)
        `when`(semesterRepository.save(any(Semester::class.java))).thenReturn(springSemester).thenReturn(fallSemester)

        `when`(courseRepository.findByUniversityIdAndCode(1L, "CS101")).thenReturn(null).thenReturn(courses.getValue("CS101"))
        `when`(courseRepository.findByUniversityIdAndCode(1L, "MATH201")).thenReturn(null)
        `when`(courseRepository.findByUniversityIdAndCode(1L, "PHYS301")).thenReturn(null)
        `when`(courseRepository.findByUniversityIdAndCode(1L, "HIST110")).thenReturn(null)
        `when`(courseRepository.findByUniversityIdAndCode(1L, "CHEM105")).thenReturn(null)
        `when`(courseRepository.findByUniversityIdAndCode(1L, "ECON210")).thenReturn(null)
        `when`(courseRepository.findByUniversityIdAndCode(1L, "ENG220")).thenReturn(null)
        `when`(courseRepository.findByUniversityIdAndCode(1L, "STAT230")).thenReturn(null)
        `when`(courseRepository.findByUniversityIdAndCode(1L, "ART150")).thenReturn(null)
        `when`(courseRepository.save(any(Course::class.java)))
            .thenReturn(courses.getValue("CS101"))
            .thenReturn(courses.getValue("MATH201"))
            .thenReturn(courses.getValue("PHYS301"))
            .thenReturn(courses.getValue("HIST110"))
            .thenReturn(courses.getValue("CHEM105"))
            .thenReturn(courses.getValue("ECON210"))
            .thenReturn(courses.getValue("ENG220"))
            .thenReturn(courses.getValue("STAT230"))
            .thenReturn(courses.getValue("ART150"))

        stubMissingLectureLookups(springSemester.id, fallSemester.id)
        `when`(lectureRepository.save(any(Lecture::class.java)))
            .thenReturn(
                lectures.getValue("2026-SPRING-CS101"),
                lectures.getValue("2026-SPRING-MATH201"),
                lectures.getValue("2026-SPRING-PHYS301"),
                lectures.getValue("2026-SPRING-HIST110"),
                lectures.getValue("2026-SPRING-CHEM105"),
                lectures.getValue("2026-SPRING-ECON210"),
                lectures.getValue("2026-SPRING-ENG220"),
                lectures.getValue("2026-SPRING-STAT230"),
                lectures.getValue("2026-SPRING-ART150"),
                lectures.getValue("2025-FALL-CS101")
            )
        stubMissingScheduleLookups()
        `when`(lectureScheduleRepository.save(any(LectureSchedule::class.java))).thenAnswer { it.arguments.first() }

        `when`(timetableRepository.findByMemberIdAndSemesterId(2L, 4L)).thenReturn(null)
        `when`(timetableRepository.save(any(Timetable::class.java))).thenReturn(timetable)
        `when`(timetableLectureRepository.existsByTimetableIdAndLectureId(40L, 5L)).thenReturn(false)
        `when`(timetableLectureRepository.existsByTimetableIdAndLectureId(40L, 6L)).thenReturn(false)

        `when`(postRepository.findByBoardIdAndTitle(20L, "Welcome to MiruMiru")).thenReturn(null).thenReturn(generalPost)
        `when`(postRepository.findByBoardIdAndTitle(21L, "Best lunch near campus?")).thenReturn(null).thenReturn(freePost)
        `when`(postRepository.save(any(Post::class.java))).thenReturn(generalPost).thenReturn(freePost)
        `when`(postImageRepository.findByPostIdAndDisplayOrder(50L, 0)).thenReturn(null)
        `when`(postImageRepository.save(any(PostImage::class.java))).thenAnswer { it.arguments.first() }

        `when`(postAnonymousMappingRepository.findByPostIdAndMemberId(51L, 2L)).thenReturn(null)
        `when`(postAnonymousMappingRepository.findByPostIdAndMemberId(51L, 3L)).thenReturn(null)
        `when`(postAnonymousMappingRepository.save(any(PostAnonymousMapping::class.java))).thenAnswer { it.arguments.first() }

        `when`(postLikeRepository.findByPostIdAndMemberId(51L, 3L)).thenReturn(null)
        `when`(postLikeRepository.save(any(PostLike::class.java))).thenAnswer { it.arguments.first() }

        `when`(commentRepository.findAllByPostIdOrderByCreatedAtAsc(51L))
            .thenReturn(emptyList())
            .thenReturn(listOf(rootComment))
            .thenReturn(listOf(rootComment))
            .thenReturn(listOf(rootComment))
        `when`(commentRepository.save(any(Comment::class.java))).thenReturn(rootComment).thenReturn(childComment)

        `when`(courseReviewRepository.findByCourseIdAndMemberId(courses.getValue("CS101").id, testMember.id)).thenReturn(null)
        `when`(courseReviewRepository.findByCourseIdAndMemberId(courses.getValue("CS101").id, emptyMember.id)).thenReturn(null)
        `when`(courseReviewRepository.save(any(CourseReview::class.java))).thenAnswer { it.arguments.first() }

        initializer.run(DefaultApplicationArguments())

        verify(universityRepository).save(any(University::class.java))
        verify(majorRepository, times(2)).save(any(Major::class.java))
        verify(memberRepository, times(2)).save(any(Member::class.java))
        verify(boardRepository, times(2)).save(any(Board::class.java))
        verify(semesterRepository, times(2)).save(any(Semester::class.java))
        verify(courseRepository, times(9)).save(any(Course::class.java))
        verify(lectureRepository, times(10)).save(any(Lecture::class.java))
        verify(lectureScheduleRepository, times(12)).save(any(LectureSchedule::class.java))
        verify(timetableRepository).save(any(Timetable::class.java))
        verify(timetableLectureRepository, times(2)).save(any(TimetableLecture::class.java))
        verify(postRepository, times(2)).save(any(Post::class.java))
        verify(postImageRepository).save(any(PostImage::class.java))
        verify(postAnonymousMappingRepository, times(2)).save(any(PostAnonymousMapping::class.java))
        verify(postLikeRepository).save(any(PostLike::class.java))
        verify(commentRepository, times(2)).save(any(Comment::class.java))
        verify(courseReviewRepository, times(2)).save(any(CourseReview::class.java))
    }

    @Test
    fun `reuses existing seed graph without creating duplicates`() {
        val university = university()
        val computerScience = major(id = 10L, university = university, code = "CS", name = "Computer Science")
        val mathematics = major(id = 11L, university = university, code = "MATH", name = "Mathematics")
        val testMember = member(id = 2L, university = university, major = computerScience, email = "test@tokyo.ac.jp", nickname = "test-user")
        val emptyMember = member(id = 3L, university = university, major = mathematics, email = "empty@tokyo.ac.jp", nickname = "empty-user")
        val generalBoard = board(id = 20L, university = university, code = "general", name = "General", isAnonymousAllowed = false)
        val freeBoard = board(id = 21L, university = university, code = "free", name = "Free Talk", isAnonymousAllowed = true)
        val springSemester = semester(id = 4L, university = university, academicYear = 2026, term = SemesterTerm.SPRING)
        val fallSemester = semester(id = 5L, university = university, academicYear = 2025, term = SemesterTerm.FALL)
        val courses = courses(university)
        val lectures = lectures(springSemester, fallSemester, computerScience, mathematics, courses)
        val timetable = Timetable(id = 40L, member = testMember, semester = springSemester)
        val generalPost = post(id = 50L, board = generalBoard, member = testMember, title = "Welcome to MiruMiru", isAnonymous = false, likeCount = 0, commentCount = 0)
        val freePost = post(id = 51L, board = freeBoard, member = testMember, title = "Best lunch near campus?", isAnonymous = true, likeCount = 1, commentCount = 2)
        val generalPostImage = PostImage(id = 60L, post = generalPost, imageUrl = "https://example.com/images/mirumiru-welcome.png", displayOrder = 0)
        val freePostAnonForAuthor = PostAnonymousMapping(id = 61L, post = freePost, member = testMember, anonNumber = 1)
        val freePostAnonForEmptyMember = PostAnonymousMapping(id = 62L, post = freePost, member = emptyMember, anonNumber = 2)
        val freePostLike = PostLike(id = 63L, post = freePost, member = emptyMember)
        val rootComment = Comment(id = 70L, post = freePost, member = emptyMember, parent = null, content = "There is a cheap curry place behind the engineering building.", isAnonymous = true, isDeleted = false)
        val childComment = Comment(id = 71L, post = freePost, member = testMember, parent = rootComment, content = "Thanks, I will try that place tomorrow.", isAnonymous = true, isDeleted = false)

        `when`(universityRepository.findByEmailDomain("tokyo.ac.jp")).thenReturn(university)
        `when`(majorRepository.findByUniversityIdAndCode(1L, "CS")).thenReturn(computerScience)
        `when`(majorRepository.findByUniversityIdAndCode(1L, "MATH")).thenReturn(mathematics)
        `when`(memberRepository.findByEmail("test@tokyo.ac.jp")).thenReturn(testMember)
        `when`(memberRepository.findByEmail("empty@tokyo.ac.jp")).thenReturn(emptyMember)
        `when`(boardRepository.findByUniversityIdAndCode(1L, "general")).thenReturn(generalBoard)
        `when`(boardRepository.findByUniversityIdAndCode(1L, "free")).thenReturn(freeBoard)
        `when`(semesterRepository.findByUniversityIdAndAcademicYearAndTerm(1L, 2026, SemesterTerm.SPRING)).thenReturn(springSemester)
        `when`(semesterRepository.findByUniversityIdAndAcademicYearAndTerm(1L, 2025, SemesterTerm.FALL)).thenReturn(fallSemester)

        stubExistingCourseLookups(courses)
        stubExistingLectureLookups(springSemester.id, fallSemester.id, lectures)
        stubExistingScheduleLookups(lectures)
        `when`(timetableRepository.findByMemberIdAndSemesterId(2L, 4L)).thenReturn(timetable)
        `when`(timetableLectureRepository.existsByTimetableIdAndLectureId(40L, 5L)).thenReturn(true)
        `when`(timetableLectureRepository.existsByTimetableIdAndLectureId(40L, 6L)).thenReturn(true)

        `when`(postRepository.findByBoardIdAndTitle(20L, "Welcome to MiruMiru")).thenReturn(generalPost)
        `when`(postRepository.findByBoardIdAndTitle(21L, "Best lunch near campus?")).thenReturn(freePost)
        `when`(postImageRepository.findByPostIdAndDisplayOrder(50L, 0)).thenReturn(generalPostImage)
        `when`(postAnonymousMappingRepository.findByPostIdAndMemberId(51L, 2L)).thenReturn(freePostAnonForAuthor)
        `when`(postAnonymousMappingRepository.findByPostIdAndMemberId(51L, 3L)).thenReturn(freePostAnonForEmptyMember)
        `when`(postLikeRepository.findByPostIdAndMemberId(51L, 3L)).thenReturn(freePostLike)
        `when`(commentRepository.findAllByPostIdOrderByCreatedAtAsc(51L)).thenReturn(listOf(rootComment, childComment))
        `when`(courseReviewRepository.findByCourseIdAndMemberId(courses.getValue("CS101").id, testMember.id))
            .thenReturn(review(id = 80L, course = courses.getValue("CS101"), member = testMember, lecture = lectures.getValue("2025-FALL-CS101"), content = "seed one"))
        `when`(courseReviewRepository.findByCourseIdAndMemberId(courses.getValue("CS101").id, emptyMember.id))
            .thenReturn(review(id = 81L, course = courses.getValue("CS101"), member = emptyMember, lecture = lectures.getValue("2026-SPRING-CS101"), content = "seed two"))

        initializer.run(DefaultApplicationArguments())

        verify(universityRepository, never()).save(any(University::class.java))
        verify(majorRepository, never()).save(any(Major::class.java))
        verify(memberRepository, never()).save(any(Member::class.java))
        verify(boardRepository, never()).save(any(Board::class.java))
        verify(semesterRepository, never()).save(any(Semester::class.java))
        verify(courseRepository, never()).save(any(Course::class.java))
        verify(lectureRepository, never()).save(any(Lecture::class.java))
        verify(lectureScheduleRepository, never()).save(any(LectureSchedule::class.java))
        verify(timetableRepository, never()).save(any(Timetable::class.java))
        verify(timetableLectureRepository, never()).save(any(TimetableLecture::class.java))
        verify(postRepository, never()).save(any(Post::class.java))
        verify(postImageRepository, never()).save(any(PostImage::class.java))
        verify(postAnonymousMappingRepository, never()).save(any(PostAnonymousMapping::class.java))
        verify(postLikeRepository, never()).save(any(PostLike::class.java))
        verify(commentRepository, never()).save(any(Comment::class.java))
        verify(courseReviewRepository, never()).save(any(CourseReview::class.java))
    }

    private fun stubMissingLectureLookups(springSemesterId: Long, fallSemesterId: Long) {
        listOf("CS101", "MATH201", "PHYS301", "HIST110", "CHEM105", "ECON210", "ENG220", "STAT230", "ART150")
            .forEach { code ->
                `when`(lectureRepository.findBySemesterIdAndCode(springSemesterId, code)).thenReturn(null)
            }
        `when`(lectureRepository.findBySemesterIdAndCode(fallSemesterId, "CS101")).thenReturn(null)
    }

    private fun stubExistingLectureLookups(springSemesterId: Long, fallSemesterId: Long, lectures: Map<String, Lecture>) {
        `when`(lectureRepository.findBySemesterIdAndCode(springSemesterId, "CS101")).thenReturn(lectures.getValue("2026-SPRING-CS101"))
        `when`(lectureRepository.findBySemesterIdAndCode(springSemesterId, "MATH201")).thenReturn(lectures.getValue("2026-SPRING-MATH201"))
        `when`(lectureRepository.findBySemesterIdAndCode(springSemesterId, "PHYS301")).thenReturn(lectures.getValue("2026-SPRING-PHYS301"))
        `when`(lectureRepository.findBySemesterIdAndCode(springSemesterId, "HIST110")).thenReturn(lectures.getValue("2026-SPRING-HIST110"))
        `when`(lectureRepository.findBySemesterIdAndCode(springSemesterId, "CHEM105")).thenReturn(lectures.getValue("2026-SPRING-CHEM105"))
        `when`(lectureRepository.findBySemesterIdAndCode(springSemesterId, "ECON210")).thenReturn(lectures.getValue("2026-SPRING-ECON210"))
        `when`(lectureRepository.findBySemesterIdAndCode(springSemesterId, "ENG220")).thenReturn(lectures.getValue("2026-SPRING-ENG220"))
        `when`(lectureRepository.findBySemesterIdAndCode(springSemesterId, "STAT230")).thenReturn(lectures.getValue("2026-SPRING-STAT230"))
        `when`(lectureRepository.findBySemesterIdAndCode(springSemesterId, "ART150")).thenReturn(lectures.getValue("2026-SPRING-ART150"))
        `when`(lectureRepository.findBySemesterIdAndCode(fallSemesterId, "CS101")).thenReturn(lectures.getValue("2025-FALL-CS101"))
    }

    private fun stubMissingScheduleLookups() {
        `when`(lectureScheduleRepository.findByLectureIdAndDayOfWeekAndStartTimeAndEndTime(5L, DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(10, 30))).thenReturn(null)
        `when`(lectureScheduleRepository.findByLectureIdAndDayOfWeekAndStartTimeAndEndTime(5L, DayOfWeek.WEDNESDAY, LocalTime.of(9, 0), LocalTime.of(10, 30))).thenReturn(null)
        `when`(lectureScheduleRepository.findByLectureIdAndDayOfWeekAndStartTimeAndEndTime(6L, DayOfWeek.TUESDAY, LocalTime.of(13, 0), LocalTime.of(14, 30))).thenReturn(null)
        `when`(lectureScheduleRepository.findByLectureIdAndDayOfWeekAndStartTimeAndEndTime(7L, DayOfWeek.MONDAY, LocalTime.of(10, 0), LocalTime.of(11, 0))).thenReturn(null)
        `when`(lectureScheduleRepository.findByLectureIdAndDayOfWeekAndStartTimeAndEndTime(8L, DayOfWeek.TUESDAY, LocalTime.of(9, 0), LocalTime.of(10, 30))).thenReturn(null)
        `when`(lectureScheduleRepository.findByLectureIdAndDayOfWeekAndStartTimeAndEndTime(9L, DayOfWeek.WEDNESDAY, LocalTime.of(14, 0), LocalTime.of(15, 30))).thenReturn(null)
        `when`(lectureScheduleRepository.findByLectureIdAndDayOfWeekAndStartTimeAndEndTime(10L, DayOfWeek.THURSDAY, LocalTime.of(10, 0), LocalTime.of(11, 30))).thenReturn(null)
        `when`(lectureScheduleRepository.findByLectureIdAndDayOfWeekAndStartTimeAndEndTime(11L, DayOfWeek.THURSDAY, LocalTime.of(13, 0), LocalTime.of(14, 30))).thenReturn(null)
        `when`(lectureScheduleRepository.findByLectureIdAndDayOfWeekAndStartTimeAndEndTime(12L, DayOfWeek.FRIDAY, LocalTime.of(9, 0), LocalTime.of(10, 30))).thenReturn(null)
        `when`(lectureScheduleRepository.findByLectureIdAndDayOfWeekAndStartTimeAndEndTime(13L, DayOfWeek.FRIDAY, LocalTime.of(11, 0), LocalTime.of(12, 30))).thenReturn(null)
        `when`(lectureScheduleRepository.findByLectureIdAndDayOfWeekAndStartTimeAndEndTime(14L, DayOfWeek.TUESDAY, LocalTime.of(10, 0), LocalTime.of(11, 30))).thenReturn(null)
        `when`(lectureScheduleRepository.findByLectureIdAndDayOfWeekAndStartTimeAndEndTime(14L, DayOfWeek.THURSDAY, LocalTime.of(10, 0), LocalTime.of(11, 30))).thenReturn(null)
    }

    private fun stubExistingScheduleLookups(lectures: Map<String, Lecture>) {
        `when`(lectureScheduleRepository.findByLectureIdAndDayOfWeekAndStartTimeAndEndTime(5L, DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(10, 30)))
            .thenReturn(schedule(lecture = lectures.getValue("2026-SPRING-CS101"), dayOfWeek = DayOfWeek.MONDAY, start = LocalTime.of(9, 0), end = LocalTime.of(10, 30)))
        `when`(lectureScheduleRepository.findByLectureIdAndDayOfWeekAndStartTimeAndEndTime(5L, DayOfWeek.WEDNESDAY, LocalTime.of(9, 0), LocalTime.of(10, 30)))
            .thenReturn(schedule(lecture = lectures.getValue("2026-SPRING-CS101"), dayOfWeek = DayOfWeek.WEDNESDAY, start = LocalTime.of(9, 0), end = LocalTime.of(10, 30)))
        `when`(lectureScheduleRepository.findByLectureIdAndDayOfWeekAndStartTimeAndEndTime(6L, DayOfWeek.TUESDAY, LocalTime.of(13, 0), LocalTime.of(14, 30)))
            .thenReturn(schedule(lecture = lectures.getValue("2026-SPRING-MATH201"), dayOfWeek = DayOfWeek.TUESDAY, start = LocalTime.of(13, 0), end = LocalTime.of(14, 30)))
        `when`(lectureScheduleRepository.findByLectureIdAndDayOfWeekAndStartTimeAndEndTime(7L, DayOfWeek.MONDAY, LocalTime.of(10, 0), LocalTime.of(11, 0)))
            .thenReturn(schedule(lecture = lectures.getValue("2026-SPRING-PHYS301"), dayOfWeek = DayOfWeek.MONDAY, start = LocalTime.of(10, 0), end = LocalTime.of(11, 0)))
        `when`(lectureScheduleRepository.findByLectureIdAndDayOfWeekAndStartTimeAndEndTime(8L, DayOfWeek.TUESDAY, LocalTime.of(9, 0), LocalTime.of(10, 30)))
            .thenReturn(schedule(lecture = lectures.getValue("2026-SPRING-HIST110"), dayOfWeek = DayOfWeek.TUESDAY, start = LocalTime.of(9, 0), end = LocalTime.of(10, 30)))
        `when`(lectureScheduleRepository.findByLectureIdAndDayOfWeekAndStartTimeAndEndTime(9L, DayOfWeek.WEDNESDAY, LocalTime.of(14, 0), LocalTime.of(15, 30)))
            .thenReturn(schedule(lecture = lectures.getValue("2026-SPRING-CHEM105"), dayOfWeek = DayOfWeek.WEDNESDAY, start = LocalTime.of(14, 0), end = LocalTime.of(15, 30)))
        `when`(lectureScheduleRepository.findByLectureIdAndDayOfWeekAndStartTimeAndEndTime(10L, DayOfWeek.THURSDAY, LocalTime.of(10, 0), LocalTime.of(11, 30)))
            .thenReturn(schedule(lecture = lectures.getValue("2026-SPRING-ECON210"), dayOfWeek = DayOfWeek.THURSDAY, start = LocalTime.of(10, 0), end = LocalTime.of(11, 30)))
        `when`(lectureScheduleRepository.findByLectureIdAndDayOfWeekAndStartTimeAndEndTime(11L, DayOfWeek.THURSDAY, LocalTime.of(13, 0), LocalTime.of(14, 30)))
            .thenReturn(schedule(lecture = lectures.getValue("2026-SPRING-ENG220"), dayOfWeek = DayOfWeek.THURSDAY, start = LocalTime.of(13, 0), end = LocalTime.of(14, 30)))
        `when`(lectureScheduleRepository.findByLectureIdAndDayOfWeekAndStartTimeAndEndTime(12L, DayOfWeek.FRIDAY, LocalTime.of(9, 0), LocalTime.of(10, 30)))
            .thenReturn(schedule(lecture = lectures.getValue("2026-SPRING-STAT230"), dayOfWeek = DayOfWeek.FRIDAY, start = LocalTime.of(9, 0), end = LocalTime.of(10, 30)))
        `when`(lectureScheduleRepository.findByLectureIdAndDayOfWeekAndStartTimeAndEndTime(13L, DayOfWeek.FRIDAY, LocalTime.of(11, 0), LocalTime.of(12, 30)))
            .thenReturn(schedule(lecture = lectures.getValue("2026-SPRING-ART150"), dayOfWeek = DayOfWeek.FRIDAY, start = LocalTime.of(11, 0), end = LocalTime.of(12, 30)))
        `when`(lectureScheduleRepository.findByLectureIdAndDayOfWeekAndStartTimeAndEndTime(14L, DayOfWeek.TUESDAY, LocalTime.of(10, 0), LocalTime.of(11, 30)))
            .thenReturn(schedule(lecture = lectures.getValue("2025-FALL-CS101"), dayOfWeek = DayOfWeek.TUESDAY, start = LocalTime.of(10, 0), end = LocalTime.of(11, 30)))
        `when`(lectureScheduleRepository.findByLectureIdAndDayOfWeekAndStartTimeAndEndTime(14L, DayOfWeek.THURSDAY, LocalTime.of(10, 0), LocalTime.of(11, 30)))
            .thenReturn(schedule(lecture = lectures.getValue("2025-FALL-CS101"), dayOfWeek = DayOfWeek.THURSDAY, start = LocalTime.of(10, 0), end = LocalTime.of(11, 30)))
    }

    private fun stubExistingCourseLookups(courses: Map<String, Course>) {
        courses.forEach { (code, course) ->
            `when`(courseRepository.findByUniversityIdAndCode(1L, code)).thenReturn(course)
        }
    }

    private fun courses(university: University): Map<String, Course> {
        return listOf(
            course(id = 100L, university = university, code = "CS101", name = "Introduction to Computer Science"),
            course(id = 101L, university = university, code = "MATH201", name = "Linear Algebra"),
            course(id = 102L, university = university, code = "PHYS301", name = "Classical Mechanics"),
            course(id = 103L, university = university, code = "HIST110", name = "World History"),
            course(id = 104L, university = university, code = "CHEM105", name = "General Chemistry"),
            course(id = 105L, university = university, code = "ECON210", name = "Microeconomics"),
            course(id = 106L, university = university, code = "ENG220", name = "Academic English"),
            course(id = 107L, university = university, code = "STAT230", name = "Statistics Fundamentals"),
            course(id = 108L, university = university, code = "ART150", name = "Visual Arts Appreciation")
        ).associateBy { it.code }
    }

    private fun lectures(
        springSemester: Semester,
        fallSemester: Semester,
        computerScience: Major,
        mathematics: Major,
        courses: Map<String, Course>
    ): Map<String, Lecture> {
        return listOf(
            lecture(id = 5L, semester = springSemester, major = computerScience, course = courses.getValue("CS101"), code = "CS101", name = "Introduction to Computer Science", professor = "Prof. Akiyama", credit = 3),
            lecture(id = 6L, semester = springSemester, major = null, course = courses.getValue("MATH201"), code = "MATH201", name = "Linear Algebra", professor = "Prof. Sato", credit = 2),
            lecture(id = 7L, semester = springSemester, major = null, course = courses.getValue("PHYS301"), code = "PHYS301", name = "Classical Mechanics", professor = "Prof. Tanaka", credit = 3),
            lecture(id = 8L, semester = springSemester, major = null, course = courses.getValue("HIST110"), code = "HIST110", name = "World History", professor = "Prof. Nakamura", credit = 2),
            lecture(id = 9L, semester = springSemester, major = null, course = courses.getValue("CHEM105"), code = "CHEM105", name = "General Chemistry", professor = "Prof. Suzuki", credit = 3),
            lecture(id = 10L, semester = springSemester, major = null, course = courses.getValue("ECON210"), code = "ECON210", name = "Microeconomics", professor = "Prof. Kobayashi", credit = 2),
            lecture(id = 11L, semester = springSemester, major = null, course = courses.getValue("ENG220"), code = "ENG220", name = "Academic English", professor = "Prof. Wilson", credit = 2),
            lecture(id = 12L, semester = springSemester, major = mathematics, course = courses.getValue("STAT230"), code = "STAT230", name = "Statistics Fundamentals", professor = "Prof. Yamamoto", credit = 2),
            lecture(id = 13L, semester = springSemester, major = null, course = courses.getValue("ART150"), code = "ART150", name = "Visual Arts Appreciation", professor = "Prof. Lee", credit = 2),
            lecture(id = 14L, semester = fallSemester, major = computerScience, course = courses.getValue("CS101"), code = "CS101", name = "Introduction to Computer Science", professor = "Prof. Ito", credit = 3)
        ).associateBy { lecture -> "${lecture.semester.academicYear}-${lecture.semester.term.name}-${lecture.code}" }
    }

    private fun university(): University {
        return University(id = 1L, name = "The University of Tokyo", emailDomain = "tokyo.ac.jp")
    }

    private fun major(id: Long, university: University, code: String, name: String): Major {
        return Major(id = id, university = university, code = code, name = name)
    }

    private fun member(id: Long, university: University, major: Major, email: String, nickname: String): Member {
        return Member(
            id = id,
            university = university,
            major = major,
            email = email,
            password = "encoded-password",
            nickname = nickname
        )
    }

    private fun board(id: Long, university: University, code: String, name: String, isAnonymousAllowed: Boolean): Board {
        return Board(
            id = id,
            university = university,
            code = code,
            name = name,
            isAnonymousAllowed = isAnonymousAllowed
        )
    }

    private fun course(id: Long, university: University, code: String, name: String): Course {
        return Course(id = id, university = university, code = code, name = name)
    }

    private fun semester(id: Long, university: University, academicYear: Int, term: SemesterTerm): Semester {
        return Semester(id = id, university = university, academicYear = academicYear, term = term)
    }

    private fun lecture(
        id: Long,
        semester: Semester,
        major: Major?,
        course: Course,
        code: String,
        name: String,
        professor: String,
        credit: Int
    ): Lecture {
        return Lecture(
            id = id,
            semester = semester,
            major = major,
            course = course,
            code = code,
            name = name,
            professor = professor,
            credit = credit
        )
    }

    private fun post(
        id: Long,
        board: Board,
        member: Member,
        title: String,
        isAnonymous: Boolean,
        likeCount: Int,
        commentCount: Int
    ): Post {
        return Post(
            id = id,
            board = board,
            member = member,
            title = title,
            content = "seed-content",
            isAnonymous = isAnonymous,
            likeCount = likeCount,
            commentCount = commentCount,
            isDeleted = false
        )
    }

    private fun schedule(lecture: Lecture, dayOfWeek: DayOfWeek, start: LocalTime, end: LocalTime): LectureSchedule {
        return LectureSchedule(
            id = lecture.id * 10 + dayOfWeek.value,
            lecture = lecture,
            dayOfWeek = dayOfWeek,
            startTime = start,
            endTime = end,
            location = "Room"
        )
    }

    private fun review(id: Long, course: Course, member: Member, lecture: Lecture, content: String): CourseReview {
        return CourseReview(
            id = id,
            course = course,
            member = member,
            lecture = lecture,
            academicYear = lecture.semester.academicYear,
            term = lecture.semester.term,
            professor = lecture.professor,
            overallRating = 4,
            difficulty = 3,
            workload = 2,
            wouldTakeAgain = true,
            content = content
        )
    }
}
