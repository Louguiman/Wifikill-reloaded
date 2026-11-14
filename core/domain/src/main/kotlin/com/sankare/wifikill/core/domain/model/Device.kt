package com.sankare.wifikill.core.domain.model

data class Device(
    val id: String,
    val customName: String?,
    val hostname: String?,
    val manufacturer: String?,
    val deviceType: DeviceType,
    val identityConfidence: IdentityConfidence,
    val isTrusted: Boolean,
    val notes: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

enum class DeviceType {
    PHONE,
    TABLET,
    COMPUTER,
    ROUTER,
    SMART_TV,
    IOT,
    PRINTER,
    UNKNOWN,
}

enum class IdentityConfidence {
    LOW,
    MEDIUM,
    HIGH,
}
