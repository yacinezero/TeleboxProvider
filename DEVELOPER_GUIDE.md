# دليل المطورين - إضافة Telebox لـ CloudStream

هذا الدليل مخصص للمطورين الذين يرغبون في فهم بنية الكود، تعديله، أو المساهمة في تطويره.

## بنية المشروع

يتبع المشروع البنية القياسية لإضافات CloudStream 3:

```
TeleboxProvider/
├── TeleboxProvider/
│   ├── src/
│   │   └── main/
│   │       ├── kotlin/
│   │       │   └── com/
│   │       │       └── telebox/
│   │       │           ├── TeleboxProvider.kt    # الملف الرئيسي للإضافة
│   │       │           └── TeleboxPlugin.kt      # ملف تسجيل الإضافة
│   │       ├── res/                              # الموارد (إن وجدت)
│   │       └── AndroidManifest.xml               # ملف Manifest
│   └── build.gradle.kts                          # إعدادات البناء للإضافة
├── gradle/                                       # ملفات Gradle Wrapper
├── build.gradle.kts                              # إعدادات البناء الرئيسية
├── settings.gradle.kts                           # إعدادات المشروع
├── gradle.properties                             # خصائص Gradle
├── repo.json                                     # ملف المستودع
├── plugins.json                                  # قائمة الإضافات
├── README.md                                     # الوثائق الأساسية
├── INSTALLATION.md                               # دليل التثبيت
└── DEVELOPER_GUIDE.md                            # هذا الملف
```

## الملفات الرئيسية

### 1. TeleboxProvider.kt

هذا هو الملف الأساسي الذي يحتوي على منطق الإضافة. يرث من `MainAPI` ويوفر الدوال التالية:

#### الدوال الأساسية

**`getMainPage(page: Int, request: MainPageRequest): HomePageResponse`**
- تُستدعى عند فتح الصفحة الرئيسية للإضافة.
- تجلب قائمة الملفات من Telebox API.
- تدعم التحميل اللانهائي (pagination).

**`search(query: String): List<SearchResponse>`**
- تُستدعى عند البحث عن ملف.
- ترسل استعلام بحث إلى API وتعيد النتائج.

**`load(url: String): LoadResponse`**
- تُستدعى عند فتح صفحة تفاصيل ملف.
- تجلب معلومات الملف (الاسم، الحجم، النوع).
- تحدد ما إذا كان الملف فيلماً أو حلقة من مسلسل.

**`loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean`**
- **الأهم**: تُستدعى عند محاولة تشغيل الفيديو.
- تجلب رابط التنزيل المباشر من Telebox API.
- تستدعي `callback` مع رابط الفيديو.

#### الدوال المساعدة

**`apiGet(endpoint: String, params: Map<String, String>): String`**
- ترسل طلب GET إلى Telebox API.
- تضيف Token تلقائياً إلى الطلب.
- تتعامل مع الأخطاء وتلقي استثناءات واضحة.

**`parseApiResponse(json: String): List<SearchResponse>`**
- تحلل استجابة JSON من API.
- تحول البيانات إلى قائمة من `SearchResponse`.
- **ملاحظة**: يستخدم Regex بسيط، يُنصح باستخدام مكتبة JSON متكاملة.

**`scrapeFilesFromWeb(): List<SearchResponse>`**
- Fallback في حال فشل API.
- يستخدم web scraping لجلب الملفات من صفحة الويب.

**`formatFileSize(bytes: Long): String`**
- تنسق حجم الملف بصيغة قابلة للقراءة (KB, MB, GB).

### 2. TeleboxPlugin.kt

ملف بسيط يسجل الإضافة في CloudStream:

```kotlin
@CloudstreamPlugin
class TeleboxPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(TeleboxProvider())
    }
}
```

### 3. build.gradle.kts (للإضافة)

يحتوي على إعدادات البناء الخاصة بالإضافة:

- **version**: رقم إصدار الإضافة.
- **cloudstream**: إعدادات خاصة بـ CloudStream (اللغة، الوصف، المؤلفون، الأنواع المدعومة).
- **dependencies**: المكتبات المطلوبة (OkHttp, Jsoup, إلخ).

## كيفية البناء والاختبار

### البناء المحلي

لبناء الإضافة محلياً، استخدم الأمر التالي:

```bash
./gradlew TeleboxProvider:make
```

سيتم إنشاء ملف `.cs3` في المجلد `TeleboxProvider/build/dist/`.

### النشر عبر ADB

إذا كان لديك جهاز Android متصل عبر ADB، يمكنك نشر الإضافة مباشرة:

```bash
./gradlew TeleboxProvider:deployWithAdb
```

**ملاحظة**: تأكد من منح CloudStream صلاحية "All Files Access" على Android 11+.

### الاختبار

1. قم ببناء الإضافة.
2. انسخ ملف `.cs3` إلى جهاز Android.
3. افتح CloudStream واذهب إلى **Settings > Extensions**.
4. اضغط على أيقونة "+" واختر الملف.
5. قم بتثبيت الإضافة واختبرها.

## التعديلات الشائعة

### تغيير endpoints الخاصة بـ API

إذا اكتشفت endpoints جديدة أو مختلفة، عدّل الثوابت في `companion object`:

```kotlin
companion object {
    private const val API_BASE_URL = "https://www.linkbox.to/api/open"
    private const val SEARCH_ENDPOINT = "/file/search"
    // أضف endpoints جديدة هنا
}
```

### تحسين تحليل JSON

الكود الحالي يستخدم Regex لتحليل JSON، وهو ليس الأفضل. لتحسينه:

1. أضف مكتبة JSON إلى `build.gradle.kts`:
   ```kotlin
   implementation("com.google.code.gson:gson:2.10.1")
   ```

2. أنشئ data classes للاستجابات:
   ```kotlin
   data class FileListResponse(
       val list: List<FileItem>,
       val total: Int
   )
   
   data class FileItem(
       val id: String,
       val name: String,
       val type: String,
       val size: Long
   )
   ```

3. استخدم Gson للتحليل:
   ```kotlin
   val gson = Gson()
   val response = gson.fromJson(json, FileListResponse::class.java)
   ```

### إضافة دعم للترجمات

إذا أردت إضافة دعم لملفات الترجمة:

1. في دالة `loadLinks`، ابحث عن ملفات `.srt` أو `.vtt` مرتبطة بالفيديو.
2. استدعِ `subtitleCallback` لكل ملف ترجمة:
   ```kotlin
   subtitleCallback.invoke(
       SubtitleFile(
           lang = "ar",
           url = subtitleUrl
       )
   )
   ```

### تحسين الأداء

- استخدم caching للنتائج المتكررة.
- قلل عدد الطلبات إلى API.
- استخدم coroutines بشكل أفضل للطلبات المتزامنة.

## المساهمة

نرحب بالمساهمات! إذا أردت المساهمة:

1. Fork المستودع.
2. أنشئ branch جديد لميزتك أو إصلاحك.
3. اكتب كوداً نظيفاً ومعلقاً.
4. اختبر التغييرات جيداً.
5. افتح Pull Request مع وصف تفصيلي.

## الموارد المفيدة

- [وثائق CloudStream للمطورين](https://recloudstream.github.io/csdocs/devs/)
- [مستودع CloudStream الرسمي](https://github.com/recloudstream/cloudstream)
- [أمثلة على إضافات أخرى](https://github.com/recloudstream/cloudstream-extensions)

## الترخيص

هذا المشروع مرخص تحت رخصة MIT. راجع ملف `LICENSE` لمزيد من التفاصيل.
