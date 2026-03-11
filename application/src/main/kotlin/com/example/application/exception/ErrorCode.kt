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
    UNAUTHORIZED("AUTH_001", "error.unauthorized", 401),
    FORBIDDEN("AUTH_002", "error.forbidden", 403),

}