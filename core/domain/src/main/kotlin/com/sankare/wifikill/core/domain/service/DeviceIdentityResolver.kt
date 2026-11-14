package com.sankare.wifikill.core.domain.service

import com.sankare.wifikill.core.domain.model.Device
import com.sankare.wifikill.core.domain.model.DeviceObservation
import com.sankare.wifikill.core.domain.model.IdentityConfidence

data class IdentityResolution(
    val deviceId: String?,
    val confidence: IdentityConfidence,
    val shouldCreateNewDevice: Boolean,
    val reasons: List<String>,
)

interface DeviceIdentityResolver {
    suspend fun resolve(
        observation: DeviceObservation,
        existingDevices: List<Device>,
    ): IdentityResolution
}
