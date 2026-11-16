package com.sankare.wifikill.core.common

data class SanitizedErrorReport(
    val code: AppErrorCode,
    val message: String,
    val throwableType: String? = null,
)

fun interface ErrorReporter {
    fun report(report: SanitizedErrorReport)
}

object NoOpErrorReporter : ErrorReporter {
    override fun report(report: SanitizedErrorReport) = Unit
}

object ErrorSanitizer {

    fun from(error: AppError): SanitizedErrorReport = SanitizedErrorReport(
        code = error.code,
        message = sanitizeMessage(error.message) ?: error.code.name,
    )

    fun from(throwable: Throwable): SanitizedErrorReport = SanitizedErrorReport(
        code = AppErrorCode.UNKNOWN,
        message = sanitizeMessage(throwable.message) ?: throwable.javaClass.simpleName,
        throwableType = throwable.javaClass.name,
    )

    fun sanitizeMessage(message: String?): String? {
        val trimmed = message?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val redacted = URL_CREDENTIALS.replace(
            SECRET_ASSIGNMENT.replace(trimmed, "$1=[redacted]"),
            "$1[redacted]@",
        )
        return redacted.take(MAX_MESSAGE_LENGTH)
    }

    private const val MAX_MESSAGE_LENGTH = 512

    private val SECRET_ASSIGNMENT = Regex(
        "(?i)\\b(password|passwd|pwd|token|secret|authorization|api[_-]?key)\\b" +
            "\\s*[:=]\\s*(?:(?:Bearer|Basic)\\s+)?\\S+",
    )

    private val URL_CREDENTIALS = Regex("(://)[^/@\\s]+@")
}
