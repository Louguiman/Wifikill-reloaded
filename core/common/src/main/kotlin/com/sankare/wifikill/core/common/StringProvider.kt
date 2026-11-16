package com.sankare.wifikill.core.common

fun interface StringProvider {
    fun get(resourceId: Int, vararg args: Any): String
}
