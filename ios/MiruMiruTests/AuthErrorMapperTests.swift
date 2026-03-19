import XCTest
@testable import MiruMiru

final class AuthErrorMapperTests: XCTestCase {
    func testMapsLoginUnauthorizedToInvalidCredentials() {
        let error = APIClientError.server(
            statusCode: 401,
            payload: APIErrorPayload(
                code: "AUTH_001",
                message: "error.unauthorized",
                detail: APIErrorDetail.none
            )
        )

        XCTAssertEqual(
            AuthErrorMapper.map(apiError: error, context: .login),
            .invalidCredentials
        )
    }

    func testMapsRestoreUnauthorizedToInvalidSession() {
        let error = APIClientError.server(
            statusCode: 401,
            payload: APIErrorPayload(
                code: "AUTH_001",
                message: "error.unauthorized",
                detail: .message("Authorization header missing or invalid")
            )
        )

        XCTAssertEqual(
            AuthErrorMapper.map(apiError: error, context: .restoreProbe),
            .invalidSession
        )
    }

    func testMapsValidationPayloadToFieldError() {
        let error = APIClientError.server(
            statusCode: 400,
            payload: APIErrorPayload(
                code: "COMMON_001",
                message: "error.invalid_input",
                detail: .validation(
                    field: "email",
                    reason: "이메일 형식이 올바르지 않습니다",
                    rejectedValue: "bad"
                )
            )
        )

        XCTAssertEqual(
            AuthErrorMapper.map(apiError: error, context: .login),
            .fieldValidation(field: "email", reason: "이메일 형식이 올바르지 않습니다")
        )
    }

    func testMapsSignupBusinessErrors() {
        let cases: [(String, AuthError)] = [
            ("AUTH_003", .duplicateEmail),
            ("AUTH_004", .duplicateNickname),
            ("AUTH_005", .emailNotVerified),
            ("AUTH_006", .authCodeNotFound),
            ("AUTH_007", .authCodeMismatch),
            ("AUTH_008", .duplicateEmail),
            ("AUTH_010", .unregisteredUniversity),
            ("AUTH_011", .invalidMajorSelection)
        ]

        for (code, expected) in cases {
            let error = APIClientError.server(
                statusCode: 400,
                payload: APIErrorPayload(
                    code: code,
                    message: "message",
                    detail: Optional<APIErrorDetail>.none
                )
            )

            XCTAssertEqual(
                AuthErrorMapper.map(apiError: error, context: .signup),
                expected,
                "Unexpected mapping for \(code)"
            )
        }
    }
}
