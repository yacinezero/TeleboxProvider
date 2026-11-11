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
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import java.net.URLEncoder

/**
 * Telebox/Linkbox provider.
 *
 * Public Linkbox share links use the documented-by-example share_out_list API
 * found in ibnsamy96/linkbox-downloader. Private Telebox browsing uses the
 * file_search API found in TheWNetwork/twn-api-upload. Both APIs are unofficial.
 */
class TeleboxProvider : MainAPI() {
    override var mainUrl = "https://www.linkbox.to"
    override var name = "Telebox / Linkbox"
    override var lang = "all"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Others)
    override val hasMainPage = true
    override val hasQuickSearch = false

    private val mapper = jacksonObjectMapper()

    private val prefs
        get() = appContext?.getSharedPreferences(TeleboxPlugin.PREFS_NAME, Context.MODE_PRIVATE)
    private val token get() = prefs?.getString(TeleboxPlugin.KEY_TOKEN, "")?.trim().orEmpty()
    private val folderId get() = prefs?.getString(TeleboxPlugin.KEY_FOLDER_ID, "0")?.trim().orEmpty().ifBlank { "0" }
    private val shareUrl get() = prefs?.getString(TeleboxPlugin.KEY_SHARE_URL, "")?.trim().orEmpty()

    private fun requireConfigured() {
        if (shareUrl.isBlank() && token.isBlank()) {
            throw ErrorLoadingException("افتح إعدادات Telebox وأدخل رابط مشاركة Linkbox أو API Token")
        }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())

    private suspend fun privateSearch(query: String): List<JsonNode> {
        requireConfigured()
        if (token.isBlank()) return emptyList()
        val url = "$mainUrl/api/open/file_search" +
            "?token=${encode(token)}&pid=${encode(folderId)}&name=${encode(query)}&pageNo=1&pageSize=50"
        return mapper.readTree(app.get(url).text).path("data").path("list").toList()
    }

    private fun shareParts(): Pair<String, String> {
        val uri = Uri.parse(shareUrl)
        val shareToken = uri.pathSegments.lastOrNull().orEmpty()
        if (shareToken.isBlank()) throw ErrorLoadingException("رابط مشاركة Linkbox غير صالح")
        return shareToken to (uri.getQueryParameter("pid") ?: "0")
    }

    private suspend fun publicShareList(pid: String): List<JsonNode> {
        val (shareToken, _) = shareParts()
        val url = "$mainUrl/api/file/share_out_list/?pageSize=50&shareToken=${encode(shareToken)}&pid=${encode(pid)}"
        val root = mapper.readTree(app.get(url).text)
        if (!root.has("data")) throw ErrorLoadingException("لم يتم العثور على بيانات رابط المشاركة")
        return root.path("data").path("list").toList()
    }

    private fun displayName(node: JsonNode): String =
        node.path("name").asText(node.path("fileName").asText("Unnamed file"))

    private fun directUrl(node: JsonNode): String? =
        listOf("url", "downloadUrl", "dlink", "download_url").asSequence()
            .map { node.path(it).asText("") }
            .firstOrNull { it.startsWith("http://") || it.startsWith("https://") }

    private fun toSearch(node: JsonNode, direct: String?): SearchResponse? {
        val url = direct ?: directUrl(node) ?: return null
        return newMovieSearchResponse(displayName(node), url, TvType.Movie)
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        requireConfigured()
        val nodes = if (shareUrl.isNotBlank()) {
            val (_, pid) = shareParts()
            publicShareList(pid)
        } else privateSearch(" ")
        val items = nodes.filter { it.path("type").asText() != "dir" }
            .mapNotNull { toSearch(it, null) }
        return newHomePageResponse(listOf(HomePageList("Telebox files", items)), false)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val nodes = if (shareUrl.isNotBlank()) {
            publicShareList(shareParts().second).filter { displayName(it).contains(query, true) }
        } else privateSearch(query)
        return nodes.mapNotNull { toSearch(it, null) }
    }

    override suspend fun load(url: String): LoadResponse =
        newMovieLoadResponse(url.substringAfterLast('/').ifBlank { "Telebox file" }, url, TvType.Movie, url)

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val direct = data.takeIf { it.startsWith("http://") || it.startsWith("https://") }
            ?: throw ErrorLoadingException("لم يعُد API رابط تنزيل مباشر لهذا الملف")
        callback(
            newExtractorLink(
                source = name,
                name = "Telebox direct link",
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
    }
}
