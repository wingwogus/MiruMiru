package com.example.api.controller

import com.example.api.common.ApiResponse
import com.example.api.dto.member.MemberResponses
import com.example.application.member.MemberQueryService
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/members")
class MemberController(
    private val memberQueryService: MemberQueryService
) {

    @GetMapping("/me")
    fun getMyProfile(@AuthenticationPrincipal userId: String): ResponseEntity<ApiResponse<MemberResponses.MemberProfileResponse>> {
        val response = memberQueryService.getMyProfile(userId)
        return ResponseEntity.ok(ApiResponse.ok(MemberResponses.MemberProfileResponse.from(response)))
    }
}
