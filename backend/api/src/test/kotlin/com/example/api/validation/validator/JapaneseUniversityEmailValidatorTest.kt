package com.example.api.validation.validator

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import jakarta.validation.ConstraintValidatorContext

class JapaneseUniversityEmailValidatorTest {
    private val validator = JapaneseUniversityEmailValidator()
    private val context = mock(ConstraintValidatorContext::class.java)

    @Test
    fun `accepts ac jp emails`() {
        assertTrue(validator.isValid("user@tokyo.ac.jp", context))
        assertTrue(validator.isValid("USER@KYOTO-U.AC.JP", context))
    }

    @Test
    fun `rejects non ac jp emails`() {
        assertFalse(validator.isValid("user@gmail.com", context))
        assertFalse(validator.isValid("user@university.jp", context))
    }
}
