package com.telebox

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
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import java.net.URLEncoder

/**
 * Experimental Linkbox/Telebox provider.
 *
 * The upstream API is undocumented. The endpoint names below are based on the
 * public TheWNetwork client, not on a stable official API. No demo content or
 * unrelated DEX is used here. A real token must be supplied by the eventual
 * CloudStream settings UI before this provider can be enabled for users.
 */
class TeleboxProvider : MainAPI() {
    override var mainUrl = "https://www.linkbox.to"
    override var name = "Telebox (experimental)"
    override var lang = "all"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Others)
    override val hasMainPage = true
    override val hasQuickSearch = false

    private val mapper = jacksonObjectMapper()

    // Deliberately empty until a CloudStream settings surface is implemented.
    private val token: String = ""
    private val rootFolderId: String = "0"

    private fun requireToken() {
        if (token.isBlank()) {
            throw ErrorLoadingException(
                "Telebox token is not configured. Configure a token before enabling this provider."
            )
        }
    }

    private suspend fun searchFiles(query: String, folderId: String): List<JsonNode> {
        requireToken()
        val url = "$mainUrl/api/open/file_search" +
            "?token=${encode(token)}&pid=${encode(folderId)}&name=${encode(query)}&pageNo=1&pageSize=50"
        val root = mapper.readTree(app.get(url).text)
        return root.path("data").path("list").toList()
    }

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())

    private fun displayName(node: JsonNode): String =
        node.path("name").asText(node.path("fileName").asText("Unnamed file"))

    private fun fileUrl(node: JsonNode): String? {
        val candidates = listOf("url", "downloadUrl", "dlink", "download_url")
        return candidates.asSequence()
            .map { node.path(it).asText("") }
            .firstOrNull { it.startsWith("http://") || it.startsWith("https://") }
    }

    private fun toSearch(node: JsonNode): SearchResponse {
        val name = displayName(node)
        val id = node.path("id").asText(node.path("fid").asText(name))
        return newMovieSearchResponse(name, "$mainUrl/telebox/$id", TvType.Movie)
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val items = searchFiles("", rootFolderId).map(::toSearch)
        return newHomePageResponse(listOf(HomePageList("Telebox files", items)), false)
    }

    override suspend fun search(query: String): List<SearchResponse> =
        searchFiles(query, rootFolderId).map(::toSearch)

    override suspend fun load(url: String): LoadResponse {
        val name = url.substringAfterLast('/').ifBlank { "Telebox file" }
        return newMovieLoadResponse(name, url, TvType.Movie, url)
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        requireToken()
        val id = data.substringAfterLast('/').substringBefore('?')
        val node = searchFiles(id, rootFolderId).firstOrNull()
            ?: throw ErrorLoadingException("Telebox file was not found")
        val direct = fileUrl(node) ?: throw ErrorLoadingException(
            "Telebox returned no direct media URL; the undocumented response format may have changed"
        )
        callback(
            newExtractorLink(
                source = name,
                name = displayName(node),
                url = direct,
                type = if (direct.contains(".m3u8", ignoreCase = true)) "HLS" else "HTTP"
            ) {
                referer = mainUrl
                quality = Qualities.Unknown.value
            }
        )
        return true
    }
}
