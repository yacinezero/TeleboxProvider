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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

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
    private val folderSemaphore = Semaphore(6)

    private data class CachedList(val expiresAt: Long, val items: List<JsonNode>)
    private val accountListCache = ConcurrentHashMap<String, CachedList>()

    private val savedShareUrl: String
        get() = appContext?.getSharedPreferences(TeleboxPlugin.PREFS_NAME, Context.MODE_PRIVATE)
            ?.getString(TeleboxPlugin.KEY_SHARE_URL, "")?.trim().orEmpty()

    private val savedToken: String
        get() = appContext?.getSharedPreferences(TeleboxPlugin.PREFS_NAME, Context.MODE_PRIVATE)
            ?.getString(TeleboxPlugin.KEY_TOKEN, "")?.trim().orEmpty()

    private val savedEmail: String
        get() = appContext?.getSharedPreferences(TeleboxPlugin.PREFS_NAME, Context.MODE_PRIVATE)
            ?.getString(TeleboxPlugin.KEY_EMAIL, "").orEmpty().trim()

    private val hasPrivateLogin: Boolean
        get() = appContext?.getSharedPreferences(TeleboxPlugin.PREFS_NAME, Context.MODE_PRIVATE)
            ?.let { it.getString(TeleboxPlugin.KEY_EMAIL, "").orEmpty().isNotBlank() &&
                it.getString(TeleboxPlugin.KEY_PASSWORD, "").orEmpty().isNotBlank() } == true

    private val postersEnabled: Boolean
        get() = appContext?.getSharedPreferences(TeleboxPlugin.PREFS_NAME, Context.MODE_PRIVATE)
            ?.getBoolean(TeleboxPlugin.KEY_POSTERS_ENABLED, true) != false

    private val tmdbKey: String
        get() = appContext?.getSharedPreferences(TeleboxPlugin.PREFS_NAME, Context.MODE_PRIVATE)
            ?.getString(TeleboxPlugin.KEY_TMDB_KEY, "").orEmpty().trim()

    private suspend fun posterFor(node: JsonNode, isSeries: Boolean = true): String? {
        if (!postersEnabled) return null
        return MetadataManager.poster(displayName(node), null, isSeries, tmdbKey)
    }

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
        return if (isDirectory(node)) {
            "$mainUrl/d/$token?pid=$id&tb=folder"
        } else {
            "$mainUrl/telebox-file?itemId=${encode(itemId)}&shareToken=${encode(token)}&name=${encode(displayName(node))}"
        }
    }

    private fun accountNodeUrl(node: JsonNode): String {
        val id = node.path("id").asLong(0)
        val itemId = itemId(node)
        return if (isDirectory(node)) {
            "$mainUrl/telebox-account/folder/$id"
        } else {
            "$mainUrl/telebox-account/file/${encode(itemId)}?pid=${encode(node.path("pid").asText("0"))}&name=${encode(displayName(node))}"
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
        if (uri.host.orEmpty().contains("linkbox", true) && uri.pathSegments.firstOrNull() == "telebox-account" && uri.pathSegments.getOrNull(1) == "file") {
            return uri.pathSegments.getOrNull(2)?.takeIf { it.isNotBlank() }
        }
        if (uri.host.orEmpty().contains("linkbox", true) && uri.path == "/telebox-file") {
            return uri.getQueryParameter("itemId")?.takeIf { it.isNotBlank() }
        }
        return null
    }

    private fun accountFilePid(url: String): String? {
        val uri = Uri.parse(url)
        if (uri.host.orEmpty().contains("linkbox", true) &&
            uri.pathSegments.firstOrNull() == "telebox-account" &&
            uri.pathSegments.getOrNull(1) == "file") {
            return uri.getQueryParameter("pid")?.takeIf { it.isNotBlank() }
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
        return type == "dir" || type == "sdir" || type == "folder" || type == "directory" || type == "1" ||
            (node.path("id").asText("").isNotBlank() && itemId(node).isBlank())
    }

    private fun accountFolderId(url: String): String? {
        val uri = Uri.parse(url)
        if (uri.scheme == "telebox-account" && uri.host == "folder") {
            return uri.pathSegments.firstOrNull()?.takeIf { it.isNotBlank() }
        }
        if (uri.host.orEmpty().contains("linkbox", true) && uri.pathSegments.firstOrNull() == "telebox-account" && uri.pathSegments.getOrNull(1) == "folder") {
            return uri.pathSegments.getOrNull(2)?.takeIf { it.isNotBlank() }
        }
        if (uri.host.orEmpty().contains("linkbox", true) && uri.path == "/telebox-account") {
            return uri.getQueryParameter("pid")?.takeIf { it.isNotBlank() }
        }
        return null
    }

    private suspend fun fileDetail(itemId: String): String? {
        val context = appContext
        val tokenValue = if (context != null) {
            runCatching { LinkboxSession.webToken(context) }.getOrNull()
        } else null
        val token = (tokenValue ?: savedToken).takeIf { it.isNotBlank() }?.let { "&token=${encode(it)}" }.orEmpty()
        val url = "$mainUrl/api/file/detail?itemId=${encode(itemId)}&needUser=1&needTpInfo=1&platform=web&pf=web&lan=en$token"
        val request = Request.Builder().url(url).header("User-Agent", BROWSER_UA).build()
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

    private suspend fun accountList(pid: String, forceRefresh: Boolean = false): List<JsonNode> {
        val context = appContext ?: throw ErrorLoadingException("CloudStream Context غير متاح")
        val cacheKey = "$savedEmail:$pid"
        val now = System.currentTimeMillis()
        if (!forceRefresh) {
            accountListCache[cacheKey]?.takeIf { it.expiresAt > now }?.let { return it.items }
        }
        val result = mutableListOf<JsonNode>()
        var webToken = LinkboxSession.webToken(context)
        var retriedSession = false
        var page = 1
        while (page <= 100) {
            val url = "$mainUrl/api/file/my_file_list/web?pid=${encode(pid)}&pageNo=$page&pageSize=1000&sortField=utime&sortAsc=0&platform=web&pf=web&lan=en&token=${encode(webToken)}"
            val request = Request.Builder().url(url).header("User-Agent", BROWSER_UA).build()
            val body = http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw ErrorLoadingException("Linkbox account API HTTP ${response.code}")
                response.body?.string().orEmpty()
            }
            val root = mapper.readTree(body)
            if (root.path("status").asInt(0) != 1) {
                val message = root.path("msg").asText("")
                val authFailure = message.contains(Regex("token|expired|login|auth", RegexOption.IGNORE_CASE))
                if (authFailure && !retriedSession) {
                    LinkboxSession.invalidate(context)
                    webToken = LinkboxSession.webToken(context, force = true)
                    retriedSession = true
                    continue
                }
                throw ErrorLoadingException(if (message.isBlank()) "جلسة Linkbox منتهية أو غير صالحة" else "Linkbox: $message")
            }
            val pageItems = root.path("data").path("list").toList()
                .filter { it.path("pid").asText(pid) == pid }
            result += pageItems
            if (pageItems.size < 1000) break
            page++
        }
        val immutable = result.toList()
        accountListCache[cacheKey] = CachedList(System.currentTimeMillis() + 5 * 60 * 1000L, immutable)
        return immutable
    }

    private suspend fun accountListLegacy(pid: String): List<JsonNode> {
        val url = "$mainUrl/api/open/file_search?pid=${encode(pid)}&name=&pageNo=1&pageSize=1000&token=${encode(savedToken)}"
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
        if (depth > 12 || output.size >= 2000) return
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

    private fun withFilename(url: String, name: String): String {
        if (name.isBlank() || url.contains("filename=", true)) return url
        return Uri.parse(url).buildUpon().appendQueryParameter("filename", name).build().toString()
    }

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
        if (link.isBlank() && !hasPrivateLogin) return newHomePageResponse(emptyList(), false)
        val items = if (link.isNotBlank()) {
            val (token, _) = shareParts(link)
            publicShareList(link).map { node ->
                val poster = posterFor(node, isSeries = isDirectory(node))
                newMovieSearchResponse(displayName(node), nodeUrl(token, node), TvType.Movie) { posterUrl = poster }
            }
        } else {
            val pid = appContext?.getSharedPreferences(TeleboxPlugin.PREFS_NAME, Context.MODE_PRIVATE)
                ?.getString(TeleboxPlugin.KEY_FOLDER_ID, "0").orEmpty()
            accountList(pid).map { node ->
                val poster = posterFor(node, isSeries = isDirectory(node))
                newMovieSearchResponse(displayName(node), accountNodeUrl(node), TvType.Movie) { posterUrl = poster }
            }
        }
        return newHomePageResponse(listOf(HomePageList("Linkbox files", items)), false)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val link = if (query.trim().startsWith("http", true)) query.trim() else savedShareUrl
        if (link.isNotBlank()) {
            val nodes = publicShareList(link)
            val (token, _) = shareParts(link)
            return nodes.map { node ->
                val poster = posterFor(node, isSeries = isDirectory(node))
                newMovieSearchResponse(displayName(node), nodeUrl(token, node), TvType.Movie) { posterUrl = poster }
            }
        }
        if (!hasPrivateLogin) throw ErrorLoadingException("أدخل البريد والمرور للحساب الخاص أو رابطاً عاماً")
        val pid = appContext?.getSharedPreferences(TeleboxPlugin.PREFS_NAME, Context.MODE_PRIVATE)
            ?.getString(TeleboxPlugin.KEY_FOLDER_ID, "0").orEmpty()
        return accountList(pid).map { node ->
            val poster = posterFor(node, isSeries = isDirectory(node))
            newMovieSearchResponse(displayName(node), accountNodeUrl(node), TvType.Movie) { posterUrl = poster }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val accountPid = accountFolderId(url)
        if (accountPid != null) {
            val videos = mutableListOf<Triple<JsonNode, String, Int>>()
            collectAccountVideos(accountPid, "", 0, 1, videos)
            val episodeNumbers = mutableMapOf<Int, Int>()
            return newTvSeriesLoadResponse(
                "Linkbox account videos", url, TvType.TvSeries,
                videos.map { (node, path, season) ->
                    val parsed = TitleParser.clean(path.substringAfterLast("/"))
                    val episodeNumber = (episodeNumbers[season] ?: 0) + 1
                    episodeNumbers[season] = episodeNumber
                    newEpisode(accountNodeUrl(node), {
                        name = path
                        this.season = parsed.season ?: season
                        this.episode = parsed.episode ?: episodeNumber
                    }, false)
                }
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
        season: Int,
        output: MutableList<Triple<JsonNode, String, Int>>
    ) {
        coroutineScope {
        if (depth > 12 || output.size >= 2000) return@coroutineScope
        val nodes = accountList(pid)
        val folders = nodes.filter { isDirectory(it) }
        val videoNodes = nodes.filter { !isDirectory(it) && isVideoFile(it) }
        for (node in videoNodes) {
            val name = displayName(node)
            val path = if (folderPath.isBlank()) name else "$folderPath / $name"
            output += Triple(node, path, season)
        }
        folders.mapIndexed { index, node ->
            async {
                folderSemaphore.withPermit {
                    val child = mutableListOf<Triple<JsonNode, String, Int>>()
                    val childSeason = if (depth == 0) index + 1 else season
                    collectAccountVideos(
                        node.path("id").asText(),
                        if (folderPath.isBlank()) displayName(node) else "$folderPath / ${displayName(node)}",
                        depth + 1,
                        childSeason,
                        child
                    )
                    child
                }
            }
        }.awaitAll().forEach { output += it }
        }
    }

    private fun isVideoFile(node: JsonNode): Boolean {
        val type = node.path("type").asText("").lowercase()
        val name = displayName(node).lowercase()
        return itemId(node).isNotBlank() && (
            type == "video" || type == "vid" ||
                listOf(".mp4", ".mkv", ".webm", ".avi", ".mov", ".m4v", ".ts", ".m3u8").any(name::endsWith)
            )
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val direct = when {
            embeddedUrl(data)?.let { it.startsWith("http://") || it.startsWith("https://") } == true -> embeddedUrl(data)
            fileItemId(data) != null -> {
                val id = fileItemId(data)!!
                val fileName = Uri.parse(data).getQueryParameter("name").orEmpty()
                val fresh = accountFilePid(data)?.let { pid ->
                    accountList(pid, forceRefresh = true).firstOrNull { itemId(it) == id }?.let { directUrl(it) }
                }
                withFilename(fresh ?: fileDetail(id).orEmpty(), fileName)
                    .takeIf { it.startsWith("http") }
            }
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
                headers = mapOf(
                    "User-Agent" to BROWSER_UA,
                    "Accept" to "*/*",
                    "Accept-Language" to "en-US,en;q=0.9"
                )
                quality = Qualities.Unknown.value
            }
        )
        return true
    }

    companion object {
        const val BROWSER_UA = "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36"
        @Volatile
        var appContext: Context? = null

        fun reloadSettings() {
            // Settings are read from SharedPreferences on every request.
        }
    }
}
