package com.sankare.wifikill.platform

import android.content.Context
import com.sankare.wifikill.core.common.StringProvider

class AndroidStringProvider(private val context: Context) : StringProvider {
    override fun get(resourceId: Int, vararg args: Any): String =
        if (args.isEmpty()) {
            context.getString(resourceId)
        } else {
            context.getString(resourceId, *args)
        }
}
