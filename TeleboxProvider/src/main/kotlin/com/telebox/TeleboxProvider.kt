package com.telebox

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

/**
 * Telebox Provider for CloudStream
 * إضافة بسيطة لعرض ملفات من Telebox/Linkbox
 */
class TeleboxProvider : MainAPI() {
    override var mainUrl = "https://www.linkbox.to"
    override var name = "Telebox"
    override var lang = "ar"
    
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries
    )

    override val hasMainPage = true
    override val hasQuickSearch = false

    /**
     * عرض الصفحة الرئيسية
     */
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val items = listOf(
            newMovieSearchResponse(
                name = "مثال فيلم 1",
                url = "$mainUrl/test/1",
                type = TvType.Movie
            ),
            newMovieSearchResponse(
                name = "مثال فيلم 2",
                url = "$mainUrl/test/2",
                type = TvType.Movie
            ),
            newMovieSearchResponse(
                name = "مثال مسلسل S01E01",
                url = "$mainUrl/test/3",
                type = TvType.TvSeries
            )
        )
        
        return newHomePageResponse(
            list = listOf(HomePageList("ملفات Telebox", items)),
            hasNext = false
        )
    }

    /**
     * البحث
     */
    override suspend fun search(query: String): List<SearchResponse> {
        return listOf(
            newMovieSearchResponse(
                name = "نتيجة بحث: $query",
                url = "$mainUrl/search/$query",
                type = TvType.Movie
            )
        )
    }

    /**
     * تحميل تفاصيل الملف
     */
    override suspend fun load(url: String): LoadResponse {
        return newMovieLoadResponse(
            name = "ملف Telebox",
            url = url,
            type = TvType.Movie,
            dataUrl = url
        ) {
            this.plot = "هذا ملف من Telebox. الإضافة قيد التطوير."
        }
    }

    /**
     * تحميل روابط الفيديو
     */
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // رابط تجريبي (استبدله برابط حقيقي من API)
        callback.invoke(
            ExtractorLink(
                source = name,
                name = "Telebox Link",
                url = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                referer = mainUrl,
                quality = Qualities.Unknown.value,
                isM3u8 = false
            )
        )
        
        return true
    }
}
