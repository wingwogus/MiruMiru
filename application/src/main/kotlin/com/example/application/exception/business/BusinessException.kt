package com.example.application.exception.business

import com.example.application.exception.ApplicationException
import com.example.application.exception.ErrorCode

abstract class BusinessException(
    val errorCode: ErrorCode,
    val detail: Any? = null,
    val customMessage: String? = null,
    message: String = customMessage ?: errorCode.messageKey
) : ApplicationException(message)

