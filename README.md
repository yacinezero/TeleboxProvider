# TeleboxProvider for CloudStream

مزود تجريبي لـ Telebox/Linkbox. يدعم حالياً روابط مشاركة Linkbox العامة عبر `share_out_list`. واجهات الخدمة غير رسمية وقد تتغير.

## اختبار الإضافة في CloudStream

1. ابنِ ملف `.cs3` من المشروع.
2. ثبّت الإضافة في CloudStream.
3. فعّل مزود **Telebox / Linkbox**.
4. افتح البحث داخل CloudStream.
5. الصق رابط مشاركة Linkbox عام مثل `https://www.linkbox.to/...` واضغط بحث.
6. ستظهر الملفات التي يعيدها API. عند اختيار ملف، يستخدم المزود `data.list[].url` كرابط التشغيل المباشر.

هذا المسار لا يحتاج API Token، وهو المسار الوحيد الذي تم التحقق من شكل استجابته من مشروع تنزيل مستقل.

## المشاريع المرجعية

- [`ibnsamy96/linkbox-downloader`](https://github.com/ibnsamy96/linkbox-downloader) يثبت مسار `GET /api/file/share_out_list/` ويأخذ رابط الملف من `data.list[].url`.
- [`TheWNetwork/twn-api-upload`](https://github.com/TheWNetwork/twn-api-upload) يثبت `GET /api/open/file_search` وإرسال Telebox token كمعامل `token`، لكنه مشروع رفع ولا يثبت endpoint تنزيل.
- [`rclone/rclone`](https://github.com/rclone/rclone) و[`zenden2k/uptooda`](https://github.com/zenden2k/uptooda) مفيدان كنماذج معمارية فقط، ولا يحتويان على endpoint عام يمكن نسخه إلى TeleboxProvider.

## الحالة التقنية

تمت إزالة واجهة `Plugin.openSettings` لأنها غير متاحة في مكتبة CloudStream الحديثة التي يبني بها المشروع. لذلك لا يدّعي هذا الإصدار دعم الحساب الخاص أو Token داخل CloudStream. دعم الحساب الخاص يحتاج API رسمي أو طريقة إعدادات متوافقة مع إصدار CloudStream المستهدف.

لا تستخدم Release `v4.0` القديم؛ كان يحتوي على DEX مأخوذاً من GDIndex وليس منطق Telebox.

## البناء

```bash
./gradlew TeleboxProvider:make
```

يتطلب البناء Android SDK وGradle 8.9 أو أحدث. في GitHub Actions يمكن استخدام `android-actions/setup-android` بدلاً من نسخ SDK إلى المستودع.
