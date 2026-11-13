package com.telebox

import android.content.Context
import android.net.Uri
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder

/**
 * Linkbox public-share provider.
 *
 * Paste a public Linkbox share URL into CloudStream search. The provider calls
 * share_out_list and uses data.list[].url as the direct media URL. This is the
 * only download flow currently verified from an independent client.
 */
class TeleboxProvider : MainAPI() {
    override var mainUrl = "https://www.linkbox.to"
    override var name = "Telebox / Linkbox"
    override var lang = "all"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Others)
    override val hasMainPage = true
    override val hasQuickSearch = true

    private val mapper = jacksonObjectMapper()
    private val http = OkHttpClient()

    private val savedShareUrl: String
        get() = appContext?.getSharedPreferences(TeleboxPlugin.PREFS_NAME, Context.MODE_PRIVATE)
            ?.getString(TeleboxPlugin.KEY_SHARE_URL, "")?.trim().orEmpty()

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())

    private fun shareParts(link: String): Pair<String, String> {
        val uri = Uri.parse(link.trim())
        val host = uri.host.orEmpty().lowercase()
        val segments = uri.pathSegments
        val tokenIndex = segments.indexOfLast { it.isNotBlank() }
        val shareToken = if (tokenIndex >= 0) segments[tokenIndex] else ""
        if (shareToken.isBlank() || !(host.contains("linkbox") || host == "lbx.zone")) {
            throw ErrorLoadingException("الصق رابط مشاركة Linkbox عام في خانة البحث")
        }
        return shareToken to (uri.getQueryParameter("pid") ?: "0")
    }

    private fun nodeUrl(token: String, node: JsonNode): String =
        "telebox://$token/${node.path("id").asLong(0)}"

    private fun parseNodeUrl(url: String): Pair<String, String>? {
        val uri = Uri.parse(url)
        if (uri.scheme != "telebox") return null
        val token = uri.host.orEmpty()
        val pid = uri.pathSegments.firstOrNull().orEmpty()
        if (token.isBlank() || pid.isBlank()) return null
        return token to pid
    }

    private suspend fun publicShareList(link: String): List<JsonNode> {
        val (shareToken, pid) = shareParts(link)
        val url = "$mainUrl/api/file/share_out_list/?pageSize=50&shareToken=${encode(shareToken)}&pid=${encode(pid)}"
        val request = Request.Builder().url(url).header("User-Agent", "CloudStream-TeleboxProvider/1").build()
        val body = http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw ErrorLoadingException("Linkbox API HTTP ${response.code}")
            response.body?.string() ?: throw ErrorLoadingException("Linkbox API returned an empty response")
        }
        val root = mapper.readTree(body)
        if (!root.has("data")) throw ErrorLoadingException("رابط المشاركة غير صالح أو منتهي")
        return root.path("data").path("list").toList()
    }

    private fun displayName(node: JsonNode): String =
        node.path("name").asText(node.path("fileName").asText("Unnamed file"))

    private fun directUrl(node: JsonNode): String? =
        listOf("url", "downloadUrl", "dlink", "download_url").asSequence()
            .map { node.path(it).asText("") }
            .firstOrNull { it.startsWith("http://") || it.startsWith("https://") }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val link = savedShareUrl
        if (link.isBlank()) return newHomePageResponse(emptyList(), false)
        val (token, _) = shareParts(link)
        val items = publicShareList(link).map { node ->
            newMovieSearchResponse(displayName(node), nodeUrl(token, node), TvType.Movie)
        }
        return newHomePageResponse(listOf(HomePageList("Linkbox files", items)), false)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val link = if (query.trim().startsWith("http", true)) query.trim() else savedShareUrl
        if (link.isBlank()) throw ErrorLoadingException("افتح إعدادات Telebox وأدخل رابط مشاركة Linkbox")
        val nodes = publicShareList(link)
        val (token, _) = shareParts(link)
        return nodes.map { node ->
            newMovieSearchResponse(displayName(node), nodeUrl(token, node), TvType.Movie)
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val (token, pid) = parseNodeUrl(url)
            ?: return newMovieLoadResponse("Linkbox file", url, TvType.Movie, url)
        val nodes = publicShareList("$mainUrl/d/$token?pid=$pid")
        val episodes = nodes.mapIndexed { index, node ->
            newEpisode(nodeUrl(token, node)) {
                name = displayName(node)
                season = 1
                episode = index + 1
            }
        }
        return newTvSeriesLoadResponse(
            name = "Linkbox folder",
            url = url,
            type = TvType.TvSeries,
            episodes = episodes
        )
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val direct = data.takeIf { it.startsWith("http://") || it.startsWith("https://") }
            ?: throw ErrorLoadingException("هذا الملف يحتاج رابط تشغيل مباشر من Linkbox؛ API المشاركة أعاد بيانات الملف فقط")
        callback(
            newExtractorLink(
                source = name,
                name = "Linkbox direct link",
                url = direct,
                type = if (direct.contains(".m3u8", true)) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
            ) {
                referer = mainUrl
                quality = Qualities.Unknown.value
            }
        )
        return true
    }

    companion object {
        @Volatile
        var appContext: Context? = null

        fun reloadSettings() {
            // Settings are read from SharedPreferences on every request.
        }
    }
}
