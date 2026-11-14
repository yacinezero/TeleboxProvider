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

    private val savedToken: String
        get() = appContext?.getSharedPreferences(TeleboxPlugin.PREFS_NAME, Context.MODE_PRIVATE)
            ?.getString(TeleboxPlugin.KEY_TOKEN, "")?.trim().orEmpty()

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

    private fun nodeUrl(token: String, node: JsonNode): String {
        val id = node.path("id").asLong(0)
        val itemId = itemId(node)
        val embedded = directUrl(node)?.let { "&url=${encode(it)}" }.orEmpty()
        return if (isDirectory(node)) {
            "$mainUrl/d/$token?pid=$id&tb=folder"
        } else {
            "$mainUrl/telebox-file?itemId=${encode(itemId)}&shareToken=${encode(token)}$embedded"
        }
    }

    private fun accountNodeUrl(node: JsonNode): String {
        val id = node.path("id").asLong(0)
        val itemId = itemId(node)
        val embedded = directUrl(node)?.let { "&url=${encode(it)}" }.orEmpty()
        return if (isDirectory(node)) {
            "$mainUrl/telebox-account?pid=$id"
        } else {
            "$mainUrl/telebox-file?itemId=${encode(itemId)}$embedded"
        }
    }

    private fun parseNodeUrl(url: String): Pair<String, String>? {
        val uri = Uri.parse(url)
        if (!(uri.host.orEmpty().contains("linkbox", true) || uri.host == "lbx.zone")) return null
        val token = uri.pathSegments.lastOrNull().orEmpty()
        val pid = uri.getQueryParameter("pid").orEmpty()
        if (token.isBlank() || pid.isBlank()) return null
        return token to pid
    }

    private fun fileItemId(url: String): String? {
        val uri = Uri.parse(url)
        if (uri.scheme == "telebox-file") return uri.pathSegments.firstOrNull()?.takeIf { it.isNotBlank() }
        if (uri.host.orEmpty().contains("linkbox", true) && uri.path == "/telebox-file") {
            return uri.getQueryParameter("itemId")?.takeIf { it.isNotBlank() }
        }
        return null
    }

    private fun embeddedUrl(url: String): String? = Uri.parse(url).getQueryParameter("url")

    private fun itemId(node: JsonNode): String =
        listOf("item_id", "itemId", "item_rid", "itemRid", "rid")
            .asSequence()
            .map { node.path(it).asText("") }
            .firstOrNull { it.isNotBlank() }
            .orEmpty()

    private fun isDirectory(node: JsonNode): Boolean {
        val type = node.path("type").asText("").lowercase()
        return type == "dir" || type == "folder" || type == "directory" || type == "1" ||
            (node.path("id").asText("").isNotBlank() && itemId(node).isBlank())
    }

    private fun accountFolderId(url: String): String? {
        val uri = Uri.parse(url)
        if (uri.scheme == "telebox-account" && uri.host == "folder") {
            return uri.pathSegments.firstOrNull()?.takeIf { it.isNotBlank() }
        }
        if (uri.host.orEmpty().contains("linkbox", true) && uri.path == "/telebox-account") {
            return uri.getQueryParameter("pid")?.takeIf { it.isNotBlank() }
        }
        return null
    }

    private suspend fun fileDetail(itemId: String): String? {
        val token = savedToken.takeIf { it.isNotBlank() }?.let { "&token=${encode(it)}" }.orEmpty()
        val url = "$mainUrl/api/file/detail?itemId=${encode(itemId)}&needUser=1&needTpInfo=1&platform=web&pf=web&lan=en$token"
        val request = Request.Builder().url(url).header("User-Agent", "CloudStream-TeleboxProvider/1").build()
        val body = http.newCall(request).execute().use { response -> response.body?.string().orEmpty() }
        val root = mapper.readTree(body)
        val item = root.path("data").path("itemInfo")
        return directUrl(item) ?: directUrlDeep(item)
    }

    private suspend fun publicShareList(link: String): List<JsonNode> {
        val (shareToken, pid) = shareParts(link)
        return listByTokenPid(shareToken, pid)
    }

    private suspend fun listByTokenPid(shareToken: String, pid: String): List<JsonNode> {
        val token = savedToken.takeIf { it.isNotBlank() }?.let { "&token=${encode(it)}" }.orEmpty()
        val url = "$mainUrl/api/file/share_out_list/?pageSize=50&shareToken=${encode(shareToken)}&pid=${encode(pid)}$token"
        val request = Request.Builder().url(url).header("User-Agent", "CloudStream-TeleboxProvider/1").build()
        val body = http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw ErrorLoadingException("Linkbox API HTTP ${response.code}")
            response.body?.string() ?: throw ErrorLoadingException("Linkbox API returned an empty response")
        }
        val root = mapper.readTree(body)
        if (!root.has("data")) throw ErrorLoadingException("رابط المشاركة غير صالح أو منتهي")
        return root.path("data").path("list").toList()
    }

    private suspend fun accountList(pid: String): List<JsonNode> {
        if (savedToken.isBlank()) throw ErrorLoadingException("أدخل Token للحساب الخاص")
        val url = "$mainUrl/api/open/file_search?pid=${encode(pid)}&name=&pageNo=1&pageSize=50&token=${encode(savedToken)}"
        val request = Request.Builder().url(url).header("User-Agent", "CloudStream-TeleboxProvider/1").build()
        val body = http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw ErrorLoadingException("Linkbox account API HTTP ${response.code}")
            response.body?.string().orEmpty()
        }
        val root = mapper.readTree(body)
        if (root.path("status").asInt(0) != 1) throw ErrorLoadingException("Token أو Folder ID غير صالح")
        return root.path("data").path("list").toList()
    }

    private suspend fun collectVideos(
        token: String,
        pid: String,
        folderPath: String,
        depth: Int,
        output: MutableList<Pair<JsonNode, String>>
    ) {
        if (depth > 4 || output.size >= 200) return
        for (node in listByTokenPid(token, pid)) {
            val name = displayName(node)
            val path = if (folderPath.isBlank()) name else "$folderPath / $name"
            if (isDirectory(node)) {
                collectVideos(token, node.path("id").asText(), path, depth + 1, output)
            } else if (node.path("type").asText().lowercase() == "video" || itemId(node).isNotBlank()) {
                output += node to path
            }
        }
    }

    private fun displayName(node: JsonNode): String =
        node.path("name").asText(node.path("fileName").asText("Unnamed file"))

    private fun directUrl(node: JsonNode): String? =
        listOf("url", "downloadUrl", "dlink", "download_url").asSequence()
            .map { node.path(it).asText("") }
            .firstOrNull { it.startsWith("http://") || it.startsWith("https://") }

    private fun directUrlDeep(node: JsonNode): String? {
        if (node.isObject) {
            val preferred = node.fields().asSequence()
                .filter { entry ->
                    entry.key.contains("url", true) || entry.key.contains("link", true) ||
                        entry.key.contains("play", true) || entry.key.contains("download", true)
                }
                .map { it.value.asText("") }
                .firstOrNull { it.startsWith("http://") || it.startsWith("https://") }
            if (preferred != null) return preferred
            val nested = node.fields().asSequence()
                .mapNotNull { directUrlDeep(it.value) }
                .firstOrNull()
            if (nested != null) return nested
        } else if (node.isArray) {
            val nested = node.asSequence().mapNotNull { directUrlDeep(it) }.firstOrNull()
            if (nested != null) return nested
        }
        return null
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val link = savedShareUrl
        if (link.isBlank() && savedToken.isBlank()) return newHomePageResponse(emptyList(), false)
        val items = if (link.isNotBlank()) {
            val (token, _) = shareParts(link)
            publicShareList(link).map { node -> newMovieSearchResponse(displayName(node), nodeUrl(token, node), TvType.Movie) }
        } else {
            val pid = appContext?.getSharedPreferences(TeleboxPlugin.PREFS_NAME, Context.MODE_PRIVATE)
                ?.getString(TeleboxPlugin.KEY_FOLDER_ID, "0").orEmpty()
            accountList(pid).map { node -> newMovieSearchResponse(displayName(node), accountNodeUrl(node), TvType.Movie) }
        }
        return newHomePageResponse(listOf(HomePageList("Linkbox files", items)), false)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val link = if (query.trim().startsWith("http", true)) query.trim() else savedShareUrl
        if (link.isNotBlank()) {
            val nodes = publicShareList(link)
            val (token, _) = shareParts(link)
            return nodes.map { node -> newMovieSearchResponse(displayName(node), nodeUrl(token, node), TvType.Movie) }
        }
        if (savedToken.isBlank()) throw ErrorLoadingException("أدخل رابطاً عاماً أو Token وFolder ID للحساب")
        val pid = appContext?.getSharedPreferences(TeleboxPlugin.PREFS_NAME, Context.MODE_PRIVATE)
            ?.getString(TeleboxPlugin.KEY_FOLDER_ID, "0").orEmpty()
        return accountList(pid).map { node -> newMovieSearchResponse(displayName(node), accountNodeUrl(node), TvType.Movie) }
    }

    override suspend fun load(url: String): LoadResponse {
        val accountPid = accountFolderId(url)
        if (accountPid != null) {
            val videos = mutableListOf<Pair<JsonNode, String>>()
            collectAccountVideos(accountPid, "", 0, videos)
            return newTvSeriesLoadResponse(
                "Linkbox account videos", url, TvType.TvSeries,
                videos.map { (node, path) -> newEpisode(accountNodeUrl(node), { name = path }, false) }
            )
        }
        val (token, pid) = parseNodeUrl(url)
            ?: return newMovieLoadResponse("Linkbox file", url, TvType.Movie, url)
        val videos = mutableListOf<Pair<JsonNode, String>>()
        collectVideos(token, pid, "", 0, videos)
        val episodes = videos.map { (node, path) ->
            newEpisode(nodeUrl(token, node), {
                name = path
            }, false)
        }
        return newTvSeriesLoadResponse(
            name = "Linkbox videos",
            url = url,
            type = TvType.TvSeries,
            episodes = episodes
        )
    }

    private suspend fun collectAccountVideos(
        pid: String,
        folderPath: String,
        depth: Int,
        output: MutableList<Pair<JsonNode, String>>
    ) {
        if (depth > 4 || output.size >= 200) return
        for (node in accountList(pid)) {
            val name = displayName(node)
            val path = if (folderPath.isBlank()) name else "$folderPath / $name"
            if (isDirectory(node)) {
                collectAccountVideos(node.path("id").asText(), path, depth + 1, output)
            } else if (itemId(node).isNotBlank()) {
                output += node to path
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val direct = when {
            embeddedUrl(data)?.let { it.startsWith("http://") || it.startsWith("https://") } == true -> embeddedUrl(data)
            fileItemId(data) != null -> fileDetail(fileItemId(data)!!)
            (data.startsWith("http://") || data.startsWith("https://")) &&
                !data.contains("linkbox", true) && !data.contains("lbx.zone", true) -> data
            else -> null
        } ?: throw ErrorLoadingException("Linkbox لم يعُد رابط تشغيل مباشر لهذا الملف")
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
