package com.telebox

import android.content.Context
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.lagradost.cloudstream3.ErrorLoadingException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** Web session token is different from the account/open API token. */
object LinkboxSession {
    private val mapper = jacksonObjectMapper()
    private val mutex = Mutex()
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private fun prefs(context: Context) =
        context.getSharedPreferences(TeleboxPlugin.PREFS_NAME, Context.MODE_PRIVATE)

    suspend fun webToken(context: Context, force: Boolean = false): String = mutex.withLock {
        val p = prefs(context)
        val cached = p.getString(TeleboxPlugin.KEY_WEB_TOKEN, "").orEmpty()
        val issuedAt = p.getLong(TeleboxPlugin.KEY_WEB_TOKEN_ISSUED_AT, 0L)
        if (!force && cached.isNotBlank() && System.currentTimeMillis() - issuedAt < 24 * 60 * 60 * 1000L) {
            return@withLock cached
        }

        val email = p.getString(TeleboxPlugin.KEY_EMAIL, "").orEmpty().trim()
        val password = p.getString(TeleboxPlugin.KEY_PASSWORD, "").orEmpty()
        if (email.isBlank() || password.isBlank()) {
            throw ErrorLoadingException("أدخل البريد الإلكتروني وكلمة المرور لتشغيل الحساب الخاص")
        }

        val url = "https://www.linkbox.to/api/user/login_email".toHttpUrl().newBuilder()
            .addQueryParameter("email", email)
            .addQueryParameter("pwd", password)
            .addQueryParameter("platform", "web")
            .addQueryParameter("pf", "web")
            .addQueryParameter("lan", "en")
            .build()
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", TeleboxProvider.BROWSER_UA)
            .build()
        val body = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw ErrorLoadingException("فشل اتصال تسجيل الدخول إلى Linkbox")
            response.body?.string().orEmpty()
        }
        val root = mapper.readTree(body)
        if (root.path("status").asInt(0) != 1) {
            val message = root.path("msg").asText("بيانات الدخول غير صحيحة")
            throw ErrorLoadingException("Linkbox: $message")
        }
        val token = root.path("data").path("token").asText("")
        val uid = root.path("data").path("uid").asText("")
        if (token.isBlank()) throw ErrorLoadingException("لم يُرجع Linkbox Web Token")
        p.edit()
            .putString(TeleboxPlugin.KEY_WEB_TOKEN, token)
            .putString(TeleboxPlugin.KEY_SESSION_UID, uid)
            .putLong(TeleboxPlugin.KEY_WEB_TOKEN_ISSUED_AT, System.currentTimeMillis())
            .apply()
        token
    }

    fun invalidate(context: Context) {
        prefs(context).edit()
            .remove(TeleboxPlugin.KEY_WEB_TOKEN)
            .remove(TeleboxPlugin.KEY_WEB_TOKEN_ISSUED_AT)
            .remove(TeleboxPlugin.KEY_SESSION_UID)
            .apply()
    }
}
