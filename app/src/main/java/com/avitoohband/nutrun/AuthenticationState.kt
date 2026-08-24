package com.avitoohband.nutrun

enum class AuthenticationMode {
    SIGN_IN,
    CREATE_ACCOUNT,
    RESET_PASSWORD
}

data class AuthenticationUiState(
    val mode: AuthenticationMode = AuthenticationMode.SIGN_IN,
    val busy: Boolean = false,
    val emailError: String? = null,
    val passwordError: String? = null,
    val message: String? = null
)

data class AccountDeletionUiState(
    val busy: Boolean = false,
    val error: String? = null
)

fun validateAuthenticationEmail(email: String, forReset: Boolean = false): String? {
    val trimmed = email.trim()
    if (trimmed.isEmpty()) return "Enter your email."
    if (!trimmed.contains('@')) return "Enter a valid email address."
    return null
}

fun validateAuthenticationPassword(password: String): String? {
    if (password.isEmpty()) return "Enter your password."
    if (password.length < 6) return "Password must be at least 6 characters."
    return null
}

fun passwordResetConfirmationMessage(): String =
    "If that email is registered, you'll receive password reset instructions."

fun authenticationValidationErrors(
    email: String,
    password: String,
    mode: AuthenticationMode
): Pair<String?, String?> {
    val emailError = validateAuthenticationEmail(
        email,
        forReset = mode == AuthenticationMode.RESET_PASSWORD
    )
    val passwordError = if (mode == AuthenticationMode.RESET_PASSWORD) {
        null
    } else {
        validateAuthenticationPassword(password)
    }
    return emailError to passwordError
}
