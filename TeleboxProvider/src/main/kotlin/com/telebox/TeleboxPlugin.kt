package com.telebox

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class TeleboxPlugin : Plugin() {
    override fun load(context: Context) {
        // تسجيل الـ provider
        registerMainAPI(TeleboxProvider())
    }
}
