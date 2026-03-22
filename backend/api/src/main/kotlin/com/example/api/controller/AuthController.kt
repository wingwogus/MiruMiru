package com.example.api.controller

import com.example.api.dto.auth.AuthRequests
import com.example.api.dto.auth.AuthResponses
import com.example.application.auth.AuthCommand
import com.example.application.auth.AuthService
import com.example.api.common.ApiResponse
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(
    private val authService: AuthService
) {

    @PostMapping("/login")
    fun login(@Valid @RequestBody request: AuthRequests.LoginRequest): ResponseEntity<ApiResponse<AuthResponses.TokenResponse>> {
        val response = authService.login(
            AuthCommand.Login(
                email = request.email,
                password = request.password
            )
        )
        return ResponseEntity.ok(ApiResponse.ok(AuthResponses.TokenResponse.from(response)))
    }

    @PostMapping("/reissue")
    fun reissue(@Valid @RequestBody request: AuthRequests.ReissueRequest): ResponseEntity<ApiResponse<AuthResponses.TokenResponse>> {
        val newToken = authService.reissue(
            AuthCommand.Reissue(
                accessToken = request.accessToken,
                refreshToken = request.refreshToken
            )
        )
        return ResponseEntity.ok(ApiResponse.ok(AuthResponses.TokenResponse.from(newToken)))
    }

    @PostMapping("/email/send-code")
    fun sendMessage(@Valid @RequestBody request: AuthRequests.EmailSendCodeRequest): ResponseEntity<ApiResponse<Unit>> {
        authService.sendCodeToEmail(request.email)
        return ResponseEntity.ok(ApiResponse.empty(Unit))
    }

    @PostMapping("/email/verify-code")
    fun verification(@Valid @RequestBody request: AuthRequests.EmailVerifyCodeRequest): ResponseEntity<ApiResponse<Unit>> {
        authService.verifiedCode(
            AuthCommand.VerifyEmailCode(
                email = request.email,
                code = request.code
            )
        )
        return ResponseEntity.ok(ApiResponse.empty(Unit))
    }

    @PostMapping("/verify-nickname")
    fun verificationNickname(@Valid @RequestBody request: AuthRequests.NicknameVerifyRequest): ResponseEntity<ApiResponse<Unit>> {
        authService.checkDuplicatedNickname(AuthCommand.VerifyNickname(request.nickname))
        return ResponseEntity.ok(ApiResponse.empty(Unit))
    }

    @PostMapping("/signup")
    fun signup(@Valid @RequestBody request: AuthRequests.SignUpRequest): ResponseEntity<ApiResponse<Unit>> {
        authService.signUp(
            AuthCommand.SignUp(
                email = request.email,
                password = request.password,
                nickname = request.nickname,
                majorId = request.majorId!!
            )
        )
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.empty(Unit))
    }

    @GetMapping("/majors")
    fun getMajors(@Valid @ModelAttribute request: AuthRequests.MajorListRequest): ResponseEntity<ApiResponse<List<AuthResponses.MajorOptionResponse>>> {
        val response = authService.getAvailableMajors(request.email)
            .map(AuthResponses.MajorOptionResponse::from)
        return ResponseEntity.ok(ApiResponse.ok(response))
    }

    @PostMapping("/logout")
    fun logout(@AuthenticationPrincipal userId: String): ResponseEntity<ApiResponse<Unit>> {
        authService.logout(userId)
        return ResponseEntity.ok(ApiResponse.empty(Unit))
    }
}
