package com.sankare.wifikill.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ErrorSanitizerTest {

    @Test
    fun `redacts secrets from messages`() {
        val sanitized = ErrorSanitizer.sanitizeMessage(
            "login failed password=hunter2 token: abc123 Authorization=Bearer xyz",
        )

        assertFalse(sanitized!!.contains("hunter2"))
        assertFalse(sanitized.contains("abc123"))
        assertFalse(sanitized.contains("xyz"))
        assertTrue(sanitized.contains("[redacted]"))
    }

    @Test
    fun `redacts credentials embedded in URLs`() {
        val sanitized = ErrorSanitizer.sanitizeMessage(
            "failed to reach https://admin:secret@192.168.1.1/ubus",
        )

        assertFalse(sanitized!!.contains("admin:secret"))
        assertTrue(sanitized.contains("https://[redacted]@192.168.1.1/ubus"))
    }

    @Test
    fun `caps long messages`() {
        val sanitized = ErrorSanitizer.sanitizeMessage("x".repeat(2_000))

        assertEquals(512, sanitized!!.length)
    }

    @Test
    fun `falls back to the error code when no message exists`() {
        val report = ErrorSanitizer.from(AppError(AppErrorCode.ROOT_UNAVAILABLE))

        assertEquals(AppErrorCode.ROOT_UNAVAILABLE, report.code)
        assertEquals("ROOT_UNAVAILABLE", report.message)
    }

    @Test
    fun `includes throwable type without a stack trace`() {
        val report = ErrorSanitizer.from(
            IllegalStateException("password=secret-value"),
        )

        assertEquals(AppErrorCode.UNKNOWN, report.code)
        assertEquals("java.lang.IllegalStateException", report.throwableType)
        assertFalse(report.message.contains("secret-value"))
    }
}
