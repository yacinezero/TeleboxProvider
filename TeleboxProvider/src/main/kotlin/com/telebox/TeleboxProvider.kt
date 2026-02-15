package com.telebox

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.Jsoup
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.FormBody
import java.util.concurrent.TimeUnit

/**
 * Telebox Provider for CloudStream
 * 
 * هذه الإضافة تتيح الوصول إلى ملفات Telebox/Linkbox المخزنة في حسابك
 * 
 * المتطلبات:
 * 1. Token API من https://www.linkbox.to/admin/account
 * 2. Folder ID من URL المجلد (مثال: https://www.linkbox.to/admin/share-folder/12345678)
 * 
 * التكوين:
 * - اذهب إلى Settings > Providers > Telebox
 * - أدخل الـ Token الخاص بك
 * - أدخل الـ Folder ID الأساسي (اختياري)
 */
class TeleboxProvider : MainAPI() {
    override var mainUrl = "https://www.linkbox.to"
    override var name = "Telebox"
    override var lang = "all"
    
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Anime,
        TvType.OVA
    )

    override val hasMainPage = true
    override val hasQuickSearch = false

    init {
        // This is where you declare the settings that the user will see
        // in the CloudStream app under Settings > Providers > Telebox
        settings = listOf(
            ProviderSetting(
                key = PREF_API_TOKEN,
                title = "Telebox API Token",
                description = "احصل على التوكن من https://www.linkbox.to/admin/account",
                type = ProviderSettingType.String,
                defaultValue = ""
            ),
            ProviderSetting(
                key = PREF_BASE_FOLDER,
                title = "Base Folder ID (اختياري)",
                description = "أدخل ID المجلد الأساسي (الرقم 0 للمجلد الرئيسي)",
                type = ProviderSettingType.String,
                defaultValue = "0"
            )
        )
    }

    // إعدادات المستخدم
    private val apiToken: String
        get() = getApiFromSettings()
    
    private val baseFolderId: String
        get() = getBaseFolderFromSettings()

    companion object {
        private const val API_BASE_URL = "https://www.linkbox.to/api/open"
        private const val SEARCH_ENDPOINT = "/file/search"
        private const val FOLDER_ENDPOINT = "/folder/list"
        private const val FILE_INFO_ENDPOINT = "/file/info"
        private const val DOWNLOAD_ENDPOINT = "/file/download_link"
        
        // مفاتيح الإعدادات
        private const val PREF_API_TOKEN = "telebox_api_token"
        private const val PREF_BASE_FOLDER = "telebox_base_folder"
    }

    /**
     * الحصول على Token من إعدادات المستخدم
     */
    private fun getApiFromSettings(): String {
        return getKey(PREF_API_TOKEN) ?: ""
    }

    /**
     * الحصول على Base Folder ID من إعدادات المستخدم
     */
    private fun getBaseFolderFromSettings(): String {
        return getKey(PREF_BASE_FOLDER) ?: "0"
    }

    /**
     * التحقق من وجود Token
     */
    private fun checkToken(): Boolean {
        if (apiToken.isEmpty()) {
            throw ErrorLoadingException("الرجاء إدخال Token API من الإعدادات")
        }
        return true
    }

    /**
     * إنشاء HTTP client مع timeout مناسب
     */
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * إرسال طلب GET إلى API
     */
    private fun apiGet(endpoint: String, params: Map<String, String> = emptyMap()): String {
        checkToken()
        
        val urlBuilder = StringBuilder("$API_BASE_URL$endpoint?token=$apiToken")
        params.forEach { (key, value) ->
            urlBuilder.append("&$key=$value")
        }
        
        val request = Request.Builder()
            .url(urlBuilder.toString())
            .header("User-Agent", "CloudStream/3.0")
            .get()
            .build()
        
        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            throw ErrorLoadingException("فشل الاتصال بـ API: ${response.code}")
        }
        
        return response.body?.string() ?: throw ErrorLoadingException("استجابة فارغة من API")
    }

    /**
     * تحميل الصفحة الرئيسية - عرض الملفات من المجلد الأساسي
     */
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        checkToken()
        
        val items = mutableListOf<SearchResponse>()
        
        try {
            // محاولة جلب قائمة الملفات من API
            val response = apiGet(SEARCH_ENDPOINT, mapOf(
                "pid" to baseFolderId,
                "pageNo" to page.toString(),
                "pageSize" to "50"
            ))
            
            // تحليل JSON response (مبسط - قد يحتاج تعديل حسب structure الفعلي)
            val files = parseApiResponse(response)
            items.addAll(files)
            
        } catch (e: Exception) {
            // في حالة فشل API، نحاول web scraping
            items.addAll(scrapeFilesFromWeb())
        }
        
        return newHomePageResponse(
            list = listOf(HomePageList("ملفاتي", items)),
            hasNext = items.size >= 50
        )
    }

    /**
     * تحليل JSON response من API
     */
        /**
     * تحليل استجابة API (JSON) لتحويلها إلى قائمة ملفات.
     * ملاحظة: هذا التحليل مبني على افتراضات حول بنية JSON.
     * قد تحتاج إلى تعديله أو استخدام مكتبة JSON متكاملة (مثل kotlinx.serialization) لتحليل أكثر قوة.
     */
    private fun parseApiResponse(json: String): List<SearchResponse> {
        val items = mutableListOf<SearchResponse>()
        
        try {
            // ملاحظة: هذا مثال مبسط، قد تحتاج لاستخدام مكتبة JSON مثل Gson أو kotlinx.serialization
            // للتحليل الصحيح حسب البنية الفعلية للـ API
            
            // افتراض أن الـ response يحتوي على قائمة ملفات
            // سنستخدم regex بسيط للتحليل (يجب استبداله بمحلل JSON صحيح)
            
                        // Regex لاستخراج قائمة العناصر من JSON
            val listContentRegex = """"list"\s*:\s*\[(.*?)\]""".toRegex(RegexOption.DOT_MATCHES_ALL)
            val listContent = listContentRegex.find(json)?.groupValues?.get(1) ?: return emptyList()

            // Regex لاستخراج كل عنصر (ملف أو مجلد) من القائمة
            val itemPattern = """\{\s*"id"\s*:\s*"?(\d+)"?,.*?"name"\s*:\s*"([^"]+)",.*?"type"\s*:\s*"(dir|file)".*?\}""".toRegex(RegexOption.DOT_MATCHES_ALL)

            itemPattern.findAll(listContent).forEach { match ->
                val id = match.groupValues[1]
                val name = match.groupValues[2]
                val itemType = match.groupValues[3]

                // إذا كان مجلداً، يمكننا إنشاء نتيجة بحث خاصة به
                if (itemType == "dir") {
                    items.add(
                        TvSeriesSearchResponse(
                            name = name,
                            url = "$mainUrl/folder/$id", // استخدام URL مخصص للمجلدات
                            type = TvType.TvSeries, // عرض المجلدات كمسلسلات
                            posterUrl = null
                        ) {
                            this.plot = "مجلد"
                        }
                    )
                } else {
                    // تحديد نوع المحتوى بناءً على الامتداد
                    val type = when {
                        name.contains(".mp4", true) || name.contains(".mkv", true) || name.contains(".avi", true) -> TvType.Movie
                        name.contains("S\d+E\d+", true) -> TvType.TvSeries
                        else -> TvType.Movie // افتراضي
                    }

                    items.add(
                        newMovieSearchResponse(
                            name = name,
                            url = "$mainUrl/file/$id",
                            type = type
                        ) {
                            this.posterUrl = null // Telebox لا يوفر صور مصغرة عادة
                        }
                    )
                }
            }
            
        } catch (e: Exception) {
            // في حالة فشل التحليل، نرجع قائمة فارغة
        }
        
        return items
    }

    /**
     * كشط الملفات من صفحة الويب (fallback)
     */
    private suspend fun scrapeFilesFromWeb(): List<SearchResponse> {
        val items = mutableListOf<SearchResponse>()
        
        try {
            // محاولة الوصول إلى صفحة الملفات
            val doc = app.get("$mainUrl/admin/my-files").document
            
            // كشط عناصر الملفات (يحتاج تعديل حسب HTML الفعلي)
            doc.select("div.file-item, tr.file-row").forEach { element ->
                val name = element.selectFirst("span.file-name, td.name")?.text() ?: return@forEach
                val fileId = element.attr("data-id") ?: element.selectFirst("a")?.attr("href")?.split("/")?.lastOrNull() ?: return@forEach
                
                val type = when {
                    name.contains(".mp4", true) || name.contains(".mkv", true) -> TvType.Movie
                    name.contains("S0", true) || name.contains("E0", true) -> TvType.TvSeries
                    else -> TvType.Movie
                }
                
                items.add(
                    newMovieSearchResponse(
                        name = name,
                        url = "$mainUrl/file/$fileId",
                        type = type
                    )
                )
            }
        } catch (e: Exception) {
            // في حالة فشل web scraping
        }
        
        return items
    }

    /**
     * البحث عن ملفات
     */
    override suspend fun search(query: String): List<SearchResponse> {
        checkToken()
        
        val items = mutableListOf<SearchResponse>()
        
        try {
            val response = apiGet(SEARCH_ENDPOINT, mapOf(
                "name" to query,
                "pid" to baseFolderId,
                "pageNo" to "1",
                "pageSize" to "50"
            ))
            
            items.addAll(parseApiResponse(response))
        } catch (e: Exception) {
            // fallback: web scraping
        }
        
        return items
    }

    /**
     * تحميل تفاصيل الملف
     */
    override suspend fun load(url: String): LoadResponse {
        checkToken()
        
        val fileId = url.split("/").lastOrNull() ?: throw ErrorLoadingException("معرف ملف غير صالح")
        
        // محاولة جلب معلومات الملف من API
        try {
            val response = apiGet(FILE_INFO_ENDPOINT, mapOf("id" to fileId))
            
            // تحليل معلومات الملف
            val nameMatch = """"name"\s*:\s*"([^"]+)"""".toRegex().find(response)
            val sizeMatch = """"size"\s*:\s*(\d+)""".toRegex().find(response)
            
            val name = nameMatch?.groupValues?.get(1) ?: "ملف غير معروف"
            val size = sizeMatch?.groupValues?.get(1)?.toLongOrNull() ?: 0L
            
            // تحديد نوع المحتوى
            val type = when {
                name.contains("S0", true) && name.contains("E0", true) -> TvType.TvSeries
                else -> TvType.Movie
            }
            
            return if (type == TvType.TvSeries) {
                // إذا كان مسلسل، نحاول استخراج رقم الموسم والحلقة
                val episodeRegex = """S(\d+)E(\d+)""".toRegex(RegexOption.IGNORE_CASE)
                val match = episodeRegex.find(name)
                
                newTvSeriesLoadResponse(
                    name = name,
                    url = url,
                    type = TvType.TvSeries,
                    episodes = listOf(
                        Episode(
                            data = url,
                            name = name,
                            season = match?.groupValues?.get(1)?.toIntOrNull(),
                            episode = match?.groupValues?.get(2)?.toIntOrNull()
                        )
                    )
                ) {
                    this.plot = "الحجم: ${formatFileSize(size)}"
                }
            } else {
                newMovieLoadResponse(
                    name = name,
                    url = url,
                    type = TvType.Movie,
                    dataUrl = url
                ) {
                    this.plot = "الحجم: ${formatFileSize(size)}"
                }
            }
            
        } catch (e: Exception) {
            // fallback: إنشاء response بسيط
            return newMovieLoadResponse(
                name = "ملف Telebox",
                url = url,
                type = TvType.Movie,
                dataUrl = url
            )
        }
    }

    /**
     * تحميل روابط الفيديو (الأهم!)
     */
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        checkToken()
        
        val fileId = data.split("/").lastOrNull() ?: return false
        
        try {
            // محاولة الحصول على رابط التنزيل المباشر من API
            val response = apiGet(DOWNLOAD_ENDPOINT, mapOf("id" to fileId))
            
            // استخراج رابط التنزيل من JSON
            val linkMatch = """"download_link"\s*:\s*"([^"]+)"""".toRegex().find(response)
            val downloadUrl = linkMatch?.groupValues?.get(1) ?: throw ErrorLoadingException("لم يتم العثور على رابط التنزيل")
            
            // إضافة الرابط إلى callback
            callback.invoke(
                ExtractorLink(
                    source = name,
                    name = "Telebox Direct Link",
                    url = downloadUrl,
                    referer = mainUrl,
                    quality = Qualities.Unknown.value,
                    isM3u8 = false
                )
            )
            
            return true
            
        } catch (e: Exception) {
            // محاولة fallback: web scraping للحصول على الرابط
            try {
                val doc = app.get(data).document
                val downloadLink = doc.selectFirst("a.download-button, button[data-download-url]")
                    ?.attr("href") ?: doc.selectFirst("a.download-button, button[data-download-url]")
                    ?.attr("data-download-url")
                
                if (downloadLink != null) {
                    callback.invoke(
                        ExtractorLink(
                            source = name,
                            name = "Telebox Link",
                            url = downloadLink,
                            referer = mainUrl,
                            quality = Qualities.Unknown.value,
                            isM3u8 = false
                        )
                    )
                    return true
                }
            } catch (e2: Exception) {
                // فشل web scraping أيضاً
            }
        }
        
        return false
    }

    /**
     * تنسيق حجم الملف
     */
    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
            else -> "${bytes / (1024 * 1024 * 1024)} GB"
        }
    }
}
