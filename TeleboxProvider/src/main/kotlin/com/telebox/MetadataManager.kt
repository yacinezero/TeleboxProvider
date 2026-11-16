package com.telebox

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object MetadataManager {
    private const val POSITIVE_TTL = 30L * 24 * 60 * 60 * 1000
    private const val NEGATIVE_TTL = 24L * 60 * 60 * 1000
    private const val PLACEHOLDER = "https://www.linkbox.to/favicon.ico"
    private val mapper = jacksonObjectMapper()
    private val client = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .build()
    private val semaphore = Semaphore(4)
    private data class Cached(val expiresAt: Long, val value: String?)
    private val cache = ConcurrentHashMap<String, Cached>()

    suspend fun poster(title: String, year: Int?, isSeries: Boolean, tmdbKey: String = ""): String? {
        val normalized = TitleParser.clean(title)
        val key = "${normalized.title.lowercase()}:${year ?: normalized.year ?: 0}:$isSeries"
        val now = System.currentTimeMillis()
        cache[key]?.takeIf { it.expiresAt > now }?.let { return it.value ?: PLACEHOLDER }
        val result = semaphore.withPermit {
            findPoster(normalized.title, year ?: normalized.year, isSeries, tmdbKey)
        }
        cache[key] = Cached(now + if (result == null) NEGATIVE_TTL else POSITIVE_TTL, result)
        return result ?: PLACEHOLDER
    }

    private fun findPoster(title: String, year: Int?, isSeries: Boolean, tmdbKey: String): String? {
        if (!isSeries && title.firstOrNull()?.lowercaseChar() in 'a'..'z') {
            val first = title.first().lowercaseChar()
            val url = "https://v2.sg.media-imdb.com/suggestion/$first/${encode(title)}.json"
            runCatching {
                val root = get(url) ?: return@runCatching null
                root.path("d").firstOrNull { item ->
                    val qid = item.path("qid").asText("")
                    val itemYear = item.path("y").asInt(0)
                    (qid == "movie" || qid == "tvSeries") && (year == null || itemYear == 0 || kotlin.math.abs(itemYear - year) <= 1)
                }?.path("i")?.path("imageUrl")?.asText("")?.takeIf { it.startsWith("http") }
                    ?.replace("_V1_.jpg", "_V1_QL75_UX500_.jpg")
            }.getOrNull()?.let { return it }
        }
        val tvMaze = "https://api.tvmaze.com/singlesearch/shows?q=${encode(title)}"
        runCatching {
            get(tvMaze)?.path("image")?.path("original")?.asText("")?.takeIf { it.startsWith("http") }
        }.getOrNull()?.let { return it }
        if (tmdbKey.isNotBlank()) {
            val kind = if (isSeries) "tv" else "movie"
            val url = "https://api.themoviedb.org/3/search/$kind?api_key=${encode(tmdbKey)}&query=${encode(title)}&language=en"
            runCatching {
                val path = get(url)?.path("results")?.firstOrNull()?.path("poster_path")?.asText("")
                path?.takeIf { it.isNotBlank() }?.let { "https://image.tmdb.org/t/p/w500$it" }
            }.getOrNull()?.let { return it }
        }
        return null
    }

    private fun get(url: String) = client.newCall(
        Request.Builder().url(url).header("User-Agent", TeleboxProvider.BROWSER_UA).build()
    ).execute().use { response ->
        if (!response.isSuccessful) null else response.body?.string()?.let(mapper::readTree)
    }

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())
}
