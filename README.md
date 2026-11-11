# TeleboxProvider for CloudStream

مزود تجريبي لـ Telebox/Linkbox. يدعم الآن روابط مشاركة Linkbox العامة عبر `share_out_list`، ويحتوي على مسار أولي لتصفح ملفات الحساب الخاص عبر `api/open/file_search`. واجهات الخدمة غير رسمية وقد تتغير.

## طريقة الإعداد داخل CloudStream

بعد تثبيت نسخة مبنية من الإضافة، افتح صفحة Telebox واضغط زر الإعدادات الخاص بالإضافة. ستظهر الحقول التالية:

- **رابط مشاركة Linkbox**: الصق رابط مشاركة عام مثل `https://linkbox.to/...`. هذا هو المسار الموصى به للاختبار لأنه لا يحتاج API Token.
- **Telebox API Token**: استخدمه فقط إذا كنت تختبر حسابك الخاص. لا تضعه في GitHub أو ترسله لأي شخص.
- **Base Folder ID**: رقم المجلد من رابط `admin/share-folder/...`، والقيمة الافتراضية `0`.

بعد الحفظ، ارجع إلى صفحة المزود واضغط تحديث. تظهر الملفات التي يعيدها API. عند اختيار ملف، يستخدم المزود `item.url` الذي تعيده استجابة Linkbox كرابط مباشر، ولا يخترع رابطاً من ID فقط.

## ما تعلمناه من المشاريع المرجعية

- [`ibnsamy96/linkbox-downloader`](https://github.com/ibnsamy96/linkbox-downloader) يثبت مسار روابط المشاركة العامة: `GET /api/file/share_out_list/?pageSize=50&shareToken=...&pid=...`، ويأخذ رابط الملف من `data.list[].url`.
- [`TheWNetwork/twn-api-upload`](https://github.com/TheWNetwork/twn-api-upload) يثبت `GET /api/open/file_search` وإرسال Telebox token كمعامل `token`، لكنه مشروع رفع ولا يثبت endpoint تنزيل.
- [`rclone/rclone`](https://github.com/rclone/rclone) و[`zenden2k/uptooda`](https://github.com/zenden2k/uptooda) مفيدان كنماذج معمارية فقط، ولا يحتويان على endpoint عام يمكن نسخه إلى TeleboxProvider.

## الحالة التقنية

هذا المستودع لا ينشر ملف `.cs3` تلقائياً في `plugins.json` إلى أن يتم بناء الإضافة واختبارها على جهاز Android. ملف DEX القديم في Release `v4.0` كان مأخوذاً من GDIndex، لذلك لا تستخدمه.

## البناء

```bash
./gradlew TeleboxProvider:make
```

يتطلب البناء Android SDK وGradle 8.9 أو أحدث. بعد نجاح البناء، يجب اختبار ملف `.cs3` برابط مشاركة عام أولاً، ثم اختبار Token خاص بحساب غير مهم. لا تنشر `plugins.json` قبل التحقق من أن الملف المبني من هذا المصدر.
