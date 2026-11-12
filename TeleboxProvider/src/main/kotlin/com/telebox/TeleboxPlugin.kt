package com.telebox

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class TeleboxPlugin : Plugin() {
    override fun load(context: Context) {
        TeleboxProvider.appContext = context.applicationContext
        registerMainAPI(TeleboxProvider())
        openSettings = { settingsContext ->
            (settingsContext as? AppCompatActivity)?.let { activity ->
                if (activity.supportFragmentManager.findFragmentByTag("telebox_settings") == null) {
                    TeleboxSettingsFragment().show(activity.supportFragmentManager, "telebox_settings")
                }
            }
        }
    }

    companion object {
        const val PREFS_NAME = "telebox_provider"
        const val KEY_SHARE_URL = "share_url"
        const val KEY_TOKEN = "api_token"
        const val KEY_FOLDER_ID = "folder_id"
    }
}
