package com.sankare.wifikill.data.identity

import com.sankare.wifikill.core.domain.model.Device
import com.sankare.wifikill.core.domain.model.DeviceObservation
import com.sankare.wifikill.core.domain.model.IdentityConfidence
import com.sankare.wifikill.core.domain.service.DeviceIdentityResolver
import com.sankare.wifikill.core.domain.service.IdentityResolution
import javax.inject.Inject

class DefaultDeviceIdentityResolver @Inject constructor() : DeviceIdentityResolver {

    override suspend fun resolve(
        observation: DeviceObservation,
        existingDevices: List<Device>,
    ): IdentityResolution {
        val hostname = observation.hostname?.trim()?.takeIf { it.isNotEmpty() }

        if (hostname != null) {
            val matches = existingDevices.filter { device ->
                device.hostname?.trim()?.equals(hostname, ignoreCase = true) == true
            }
            when (matches.size) {
                1 -> return IdentityResolution(
                    deviceId = matches.first().id,
                    confidence = IdentityConfidence.MEDIUM,
                    shouldCreateNewDevice = false,
                    reasons = listOf("Unique hostname match"),
                )

                in 2..Int.MAX_VALUE -> return IdentityResolution(
                    deviceId = null,
                    confidence = IdentityConfidence.LOW,
                    shouldCreateNewDevice = true,
                    reasons = listOf("Ambiguous hostname match"),
                )
            }
        }

        return IdentityResolution(
            deviceId = null,
            confidence = IdentityConfidence.LOW,
            shouldCreateNewDevice = true,
            reasons = listOf("No stable identifier available"),
        )
    }
}
