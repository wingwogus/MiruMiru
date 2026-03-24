package com.example.api.course

import com.example.ApiApplication
import com.example.application.security.TokenProvider
import com.example.domain.course.CourseReviewRepository
import com.example.domain.lecture.LectureRepository
import com.example.domain.member.MemberRepository
import com.example.domain.semester.SemesterRepository
import com.example.domain.semester.SemesterTerm
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put

@SpringBootTest(
    classes = [ApiApplication::class],
    properties = [
        "spring.mail.username=test@example.com",
        "spring.mail.password=test-password"
    ]
)
@AutoConfigureMockMvc
@ActiveProfiles("local")
class CourseReviewApiIntegrationTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val memberRepository: MemberRepository,
    @Autowired private val semesterRepository: SemesterRepository,
    @Autowired private val lectureRepository: LectureRepository,
    @Autowired private val courseReviewRepository: CourseReviewRepository,
    @Autowired private val tokenProvider: TokenProvider
) {
    private lateinit var accessToken: String
    private lateinit var emptyAccessToken: String
    private var testMemberId: Long = 0L
    private var emptyMemberId: Long = 0L
    private var cs101CourseId: Long = 0L
    private var eng220CourseId: Long = 0L
    private var eng220LectureId: Long = 0L

    @BeforeEach
    fun setUp() {
        val member = memberRepository.findByEmail("test@tokyo.ac.jp")!!
        val emptyMember = memberRepository.findByEmail("empty@tokyo.ac.jp")!!
        testMemberId = member.id
        emptyMemberId = emptyMember.id
        accessToken = tokenProvider.createAccessToken(member.id, member.role)
        emptyAccessToken = tokenProvider.createAccessToken(emptyMember.id, emptyMember.role)

        val springSemester = semesterRepository.findByUniversityIdAndAcademicYearAndTerm(
            universityId = member.university.id,
            academicYear = 2026,
            term = SemesterTerm.SPRING
        )!!
        cs101CourseId = lectureRepository.findBySemesterIdAndCode(springSemester.id, "CS101")!!.course.id
        val eng220Lecture = lectureRepository.findBySemesterIdAndCode(springSemester.id, "ENG220")!!
        eng220CourseId = eng220Lecture.course.id
        eng220LectureId = eng220Lecture.id
    }

    @AfterEach
    fun tearDown() {
        courseReviewRepository.findByCourseIdAndMemberId(eng220CourseId, testMemberId)?.let(courseReviewRepository::delete)
        courseReviewRepository.findByCourseIdAndMemberId(eng220CourseId, emptyMemberId)?.let(courseReviewRepository::delete)
    }

    @Test
    fun `get course reviews returns paginated summary for seeded course`() {
        val response = mockMvc.get("/api/v1/courses/$cs101CourseId/reviews") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
            param("page", "0")
            param("size", "1")
        }.andReturn().response

        assertEquals(200, response.status)
        assertTrue(response.contentAsString.contains("\"courseId\":$cs101CourseId"))
        assertTrue(response.contentAsString.contains("\"reviewCount\":2"))
        assertTrue(response.contentAsString.contains("\"totalElements\":2"))
        assertTrue(response.contentAsString.contains("\"totalPages\":2"))
        assertTrue(response.contentAsString.contains("\"hasNext\":true"))
    }

    @Test
    fun `get my course review returns seeded historical snapshot`() {
        val response = mockMvc.get("/api/v1/courses/$cs101CourseId/reviews/me") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
        }.andReturn().response

        assertEquals(200, response.status)
        assertTrue(response.contentAsString.contains("\"academicYear\":2025"))
        assertTrue(response.contentAsString.contains("\"term\":\"FALL\""))
        assertTrue(response.contentAsString.contains("\"professor\":\"Prof. Ito\""))
        assertTrue(response.contentAsString.contains("\"isMine\":true"))
    }

    @Test
    fun `post put delete course review manages my review lifecycle`() {
        val createResponse = mockMvc.post("/api/v1/courses/$eng220CourseId/reviews") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $emptyAccessToken")
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                  "lectureId": $eng220LectureId,
                  "overallRating": 4,
                  "difficulty": 2,
                  "workload": 3,
                  "wouldTakeAgain": true,
                  "content": "  [test] good elective  "
                }
            """.trimIndent()
        }.andReturn().response

        assertEquals(201, createResponse.status)
        assertTrue(createResponse.contentAsString.contains("\"reviewId\":"))

        val myReviewResponse = mockMvc.get("/api/v1/courses/$eng220CourseId/reviews/me") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $emptyAccessToken")
        }.andReturn().response

        assertEquals(200, myReviewResponse.status)
        assertTrue(myReviewResponse.contentAsString.contains("\"content\":\"[test] good elective\""))
        assertTrue(myReviewResponse.contentAsString.contains("\"isMine\":true"))

        val updateResponse = mockMvc.put("/api/v1/courses/$eng220CourseId/reviews/me") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $emptyAccessToken")
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                  "lectureId": $eng220LectureId,
                  "overallRating": 5,
                  "difficulty": 3,
                  "workload": 4,
                  "wouldTakeAgain": false,
                  "content": "[test] updated review"
                }
            """.trimIndent()
        }.andReturn().response

        assertEquals(200, updateResponse.status)

        val listResponse = mockMvc.get("/api/v1/courses/$eng220CourseId/reviews") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $emptyAccessToken")
        }.andReturn().response

        assertEquals(200, listResponse.status)
        assertTrue(listResponse.contentAsString.contains("\"reviewCount\":1"))
        assertTrue(listResponse.contentAsString.contains("\"wouldTakeAgain\":false"))
        assertTrue(listResponse.contentAsString.contains("\"content\":\"[test] updated review\""))

        val deleteResponse = mockMvc.delete("/api/v1/courses/$eng220CourseId/reviews/me") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $emptyAccessToken")
        }.andReturn().response

        assertEquals(200, deleteResponse.status)

        val missingResponse = mockMvc.get("/api/v1/courses/$eng220CourseId/reviews/me") {
            header(HttpHeaders.AUTHORIZATION, "Bearer $emptyAccessToken")
        }.andReturn().response

        assertEquals(404, missingResponse.status)
        assertTrue(missingResponse.contentAsString.contains("COURSE_002"))
    }

    @Test
    fun `get course reviews requires authentication`() {
        val response = mockMvc.get("/api/v1/courses/$cs101CourseId/reviews")
            .andReturn().response

        assertEquals(401, response.status)
    }
}
