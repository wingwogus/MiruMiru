package com.example.api.validation.validator

import com.example.api.validation.annotation.JapaneseUniversityEmail
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import java.util.Locale

class JapaneseUniversityEmailValidator : ConstraintValidator<JapaneseUniversityEmail, String> {
    override fun isValid(value: String?, context: ConstraintValidatorContext): Boolean {
        if (value == null || value.isBlank()) {
            return true
        }

        return value.trim().lowercase(Locale.ROOT).endsWith(".ac.jp")
    }
}
