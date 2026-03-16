package com.example.application.exception

enum class ErrorCode(
    val code: String,
    val messageKey: String,
    val status: Int
) {

    INVALID_INPUT("COMMON_001", "error.invalid_input", 400),
    INVALID_JSON("COMMON_002", "error.invalid_json", 400),
    INTERNAL_ERROR("COMMON_999", "error.internal_error", 500),
    RESOURCE_NOT_FOUND("RESOURCE_001","error.resource_not_found", 404),
    USER_NOT_FOUND("USER_001", "error.user_not_found", 404),
    USER_ALREADY_EXISTS("USER_002", "error.user_already_exists", 409),
    DUPLICATE_EMAIL("AUTH_003", "error.duplicate_email", 409),
    DUPLICATE_NICKNAME("AUTH_004", "error.duplicate_nickname", 409),
    EMAIL_NOT_VERIFIED("AUTH_005", "error.email_not_verified", 400),
    AUTH_CODE_NOT_FOUND("AUTH_006", "error.auth_code_not_found", 404),
    AUTH_CODE_MISMATCH("AUTH_007", "error.auth_code_mismatch", 400),
    ALREADY_SIGNED_EMAIL("AUTH_008", "error.already_signed_email", 409),
    ALREADY_LOGGED_OUT("AUTH_009", "error.already_logged_out", 400),
    UNAUTHORIZED("AUTH_001", "error.unauthorized", 401),
    FORBIDDEN("AUTH_002", "error.forbidden", 403),

}
