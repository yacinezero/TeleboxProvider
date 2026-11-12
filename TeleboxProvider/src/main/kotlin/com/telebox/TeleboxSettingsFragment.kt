package com.telebox

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.text.InputType
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
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
            text = "الصق رابط مشاركة Linkbox العام هنا. يمكن ترك Token وFolder ID فارغين عند استخدام رابط عام."
            setPadding(0, 0, 0, (12 * density).toInt())
        }
        val shareUrl = EditText(context).apply {
            hint = "رابط Linkbox العام"
            setText(prefs.getString(TeleboxPlugin.KEY_SHARE_URL, "") ?: "")
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            isSingleLine = true
        }
        val token = EditText(context).apply {
            hint = "Telebox API Token (اختياري)"
            setText(prefs.getString(TeleboxPlugin.KEY_TOKEN, "") ?: "")
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            isSingleLine = true
        }
        val folderId = EditText(context).apply {
            hint = "Folder ID (اختياري، الافتراضي 0)"
            setText(prefs.getString(TeleboxPlugin.KEY_FOLDER_ID, "0") ?: "0")
            inputType = InputType.TYPE_CLASS_NUMBER
            isSingleLine = true
        }
        layout.addView(help)
        layout.addView(shareUrl)
        layout.addView(token)
        layout.addView(folderId)

        return AlertDialog.Builder(context)
            .setTitle("Telebox / Linkbox Settings")
            .setView(layout)
            .setNegativeButton("إلغاء", null)
            .setPositiveButton("حفظ") { _, _ ->
                prefs.edit()
                    .putString(TeleboxPlugin.KEY_SHARE_URL, shareUrl.text.toString().trim())
                    .putString(TeleboxPlugin.KEY_TOKEN, token.text.toString().trim())
                    .putString(TeleboxPlugin.KEY_FOLDER_ID, folderId.text.toString().trim().ifBlank { "0" })
                    .apply()
                TeleboxProvider.reloadSettings()
            }
            .create().also { dialog ->
                dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
    }
}
