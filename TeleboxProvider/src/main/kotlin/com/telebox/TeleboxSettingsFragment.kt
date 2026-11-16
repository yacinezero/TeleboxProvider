package com.telebox

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.text.InputType
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ScrollView
import android.widget.Switch
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment

class TeleboxSettingsFragment : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        val prefs = context.getSharedPreferences(TeleboxPlugin.PREFS_NAME, Context.MODE_PRIVATE)
        val density = resources.displayMetrics.density
        val padding = (24 * density).toInt()
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding / 2, padding, padding / 2)
        }

        val help = TextView(context).apply {
            text = "إعداد الحساب مثل rclone: Token اختياري لواجهة Linkbox المفتوحة، والبريد وكلمة المرور مطلوبان لإنشاء جلسة الويب وتشغيل الملفات."
            setPadding(0, 0, 0, (12 * density).toInt())
        }
        val email = EditText(context).apply {
            hint = "email = بريد Linkbox (مطلوب)"
            setText(prefs.getString(TeleboxPlugin.KEY_EMAIL, "") ?: "")
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            isSingleLine = true
        }
        val password = EditText(context).apply {
            hint = "password = كلمة مرور Linkbox (مطلوبة)"
            setText(prefs.getString(TeleboxPlugin.KEY_PASSWORD, "") ?: "")
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            isSingleLine = true
        }
        val shareUrl = EditText(context).apply {
            hint = "رابط Linkbox العام"
            setText(prefs.getString(TeleboxPlugin.KEY_SHARE_URL, "") ?: "")
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            isSingleLine = true
        }
        val token = EditText(context).apply {
            hint = "token = Telebox API Token (اختياري، مثل rclone)"
            setText(prefs.getString(TeleboxPlugin.KEY_TOKEN, "") ?: "")
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            isSingleLine = true
        }
        val folderId = EditText(context).apply {
            hint = "Folder ID في حسابك (0 للمجلد الرئيسي)"
            setText(prefs.getString(TeleboxPlugin.KEY_FOLDER_ID, "0") ?: "0")
            inputType = InputType.TYPE_CLASS_NUMBER
            isSingleLine = true
        }
        val tmdbKey = EditText(context).apply {
            hint = "TMDB API Key (اختياري للبوسترات)"
            setText(prefs.getString(TeleboxPlugin.KEY_TMDB_KEY, "") ?: "")
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            isSingleLine = true
        }
        val posters = Switch(context).apply {
            text = "تفعيل البوسترات (لا يوقف التصفح عند الفشل)"
            isChecked = prefs.getBoolean(TeleboxPlugin.KEY_POSTERS_ENABLED, true)
        }
        val randomRows = Switch(context).apply {
            text = "ترتيب عشوائي للصفوف"
            isChecked = prefs.getBoolean(TeleboxPlugin.KEY_RANDOM_ROWS, false)
        }
        layout.addView(help)
        layout.addView(email)
        layout.addView(password)
        layout.addView(shareUrl)
        layout.addView(token)
        layout.addView(folderId)
        layout.addView(tmdbKey)
        layout.addView(posters)
        layout.addView(randomRows)

        return AlertDialog.Builder(context)
            .setTitle("Telebox / Linkbox Settings")
            .setView(ScrollView(context).apply { addView(layout) })
            .setNegativeButton("إلغاء", null)
            .setPositiveButton("حفظ") { _, _ ->
                prefs.edit()
                    .putString(TeleboxPlugin.KEY_EMAIL, email.text.toString().trim())
                    .putString(TeleboxPlugin.KEY_PASSWORD, password.text.toString())
                    .putString(TeleboxPlugin.KEY_SHARE_URL, shareUrl.text.toString().trim())
                    .putString(TeleboxPlugin.KEY_TOKEN, token.text.toString().trim())
                    .putString(TeleboxPlugin.KEY_FOLDER_ID, folderId.text.toString().trim().ifBlank { "0" })
                    .putString(TeleboxPlugin.KEY_TMDB_KEY, tmdbKey.text.toString().trim())
                    .putBoolean(TeleboxPlugin.KEY_POSTERS_ENABLED, posters.isChecked)
                    .putBoolean(TeleboxPlugin.KEY_RANDOM_ROWS, randomRows.isChecked)
                    .apply()
                LinkboxSession.invalidate(context)
                TeleboxProvider.reloadSettings()
            }
            .create().also { dialog ->
                dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
    }
}
