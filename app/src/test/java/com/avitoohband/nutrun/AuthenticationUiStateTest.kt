package com.avitoohband.nutrun

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AuthenticationUiStateTest {
    @Test
    fun validateAuthenticationEmailRejectsBlankAndInvalidAddresses() {
        assertEquals("Enter your email.", validateAuthenticationEmail(""))
        assertEquals("Enter your email.", validateAuthenticationEmail("   "))
        assertEquals("Enter a valid email address.", validateAuthenticationEmail("not-an-email"))
        assertNull(validateAuthenticationEmail("user@example.com"))
    }

    @Test
    fun validateAuthenticationPasswordRejectsBlankAndShortValues() {
        assertEquals("Enter your password.", validateAuthenticationPassword(""))
        assertEquals("Password must be at least 6 characters.", validateAuthenticationPassword("12345"))
        assertNull(validateAuthenticationPassword("123456"))
    }

    @Test
    fun authenticationValidationErrorsSkipsPasswordForResetMode() {
        val (emailError, passwordError) = authenticationValidationErrors(
            email = "bad",
            password = "123",
            mode = AuthenticationMode.RESET_PASSWORD
        )
        assertEquals("Enter a valid email address.", emailError)
        assertNull(passwordError)
    }

    @Test
    fun authenticationValidationErrorsChecksBothFieldsForSignIn() {
        val (emailError, passwordError) = authenticationValidationErrors(
            email = "bad",
            password = "123",
            mode = AuthenticationMode.SIGN_IN
        )
        assertEquals("Enter a valid email address.", emailError)
        assertEquals("Password must be at least 6 characters.", passwordError)
    }

    @Test
    fun passwordResetConfirmationMessageDoesNotRevealAccountExistence() {
        assertEquals(
            "If that email is registered, you'll receive password reset instructions.",
            passwordResetConfirmationMessage()
        )
    }
}
