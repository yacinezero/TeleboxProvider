package com.telebox

import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class TeleboxPlugin : BasePlugin() {
    override fun load() {
        registerMainAPI(TeleboxProvider())
    }
}
