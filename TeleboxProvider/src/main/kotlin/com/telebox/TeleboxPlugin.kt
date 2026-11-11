package com.telebox

import android.app.AlertDialog
import android.content.Context
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class TeleboxPlugin : Plugin() {
    override fun load(context: Context) {
        TeleboxProvider.appContext = context.applicationContext
        registerMainAPI(TeleboxProvider())

        openSettings = { settingsContext ->
            val prefs = settingsContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val layout = LinearLayout(settingsContext).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(48, 8, 48, 0)
            }

            fun field(label: String, value: String, secret: Boolean = false): EditText {
                val input = EditText(settingsContext).apply {
                    hint = label
                    setText(value)
                    if (secret) inputType = android.text.InputType.TYPE_CLASS_TEXT or
                        android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
                }
                layout.addView(input)
                return input
            }

            val help = TextView(settingsContext).apply {
                text = "للروابط العامة: الصق رابط مشاركة Linkbox مثل https://linkbox.to/...\n" +
                    "للحساب الخاص: أدخل API Token وFolder ID. لا تشارك Token مع أي شخص."
                setPadding(0, 0, 0, 12)
            }
            layout.addView(help)

            val shareUrl = field("رابط مشاركة Linkbox (اختياري)", prefs.getString(KEY_SHARE_URL, "") ?: "")
            val token = field("Telebox API Token (اختياري)", prefs.getString(KEY_TOKEN, "") ?: "", secret = true)
            val folderId = field("Base Folder ID (اختياري، الافتراضي 0)", prefs.getString(KEY_FOLDER_ID, "0") ?: "0")

            AlertDialog.Builder(settingsContext)
                .setTitle("Telebox / Linkbox")
                .setView(layout)
                .setNegativeButton("إلغاء", null)
                .setPositiveButton("حفظ") { _, _ ->
                    prefs.edit()
                        .putString(KEY_SHARE_URL, shareUrl.text.toString().trim())
                        .putString(KEY_TOKEN, token.text.toString().trim())
                        .putString(KEY_FOLDER_ID, folderId.text.toString().trim().ifBlank { "0" })
                        .apply()
                }
                .show()
        }
    }

    companion object {
        const val PREFS_NAME = "telebox_provider"
        const val KEY_SHARE_URL = "share_url"
        const val KEY_TOKEN = "api_token"
        const val KEY_FOLDER_ID = "folder_id"
    }
}
