package com.example.api.validation.annotation

import com.example.api.validation.validator.JapaneseUniversityEmailValidator
import jakarta.validation.Constraint
import jakarta.validation.Payload
import kotlin.reflect.KClass

@Target(AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
@Constraint(validatedBy = [JapaneseUniversityEmailValidator::class])
annotation class JapaneseUniversityEmail(
    val message: String = "일본 대학 이메일(.ac.jp)만 허용됩니다",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = []
)
