package com.sankare.wifikill.core.testing

import com.sankare.wifikill.core.common.IdGenerator
import com.sankare.wifikill.core.common.TimeProvider

class FakeTimeProvider(var currentTime: Long = 0L) : TimeProvider {
    override fun nowMillis(): Long = currentTime
}

class FakeIdGenerator(private val prefix: String = "id") : IdGenerator {
    private var counter = 0

    override fun newId(): String {
        counter += 1
        return "$prefix-$counter"
    }
}
