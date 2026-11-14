package com.sankare.wifikill.data.event

import com.sankare.wifikill.core.domain.service.AppEvent
import com.sankare.wifikill.core.domain.service.AppEventBus
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class DefaultAppEventBus @Inject constructor() : AppEventBus {

    private val mutableEvents = MutableSharedFlow<AppEvent>(extraBufferCapacity = 32)

    override val events: SharedFlow<AppEvent> = mutableEvents.asSharedFlow()

    override suspend fun emit(event: AppEvent) {
        mutableEvents.emit(event)
    }
}
