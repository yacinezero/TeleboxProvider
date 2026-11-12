# TeleboxProvider for CloudStream

مزود Telebox / Linkbox لـ CloudStream. الإصدار الحالي يحتوي على زر Settings حقيقي داخل CloudStream، وفق نمط الإضافات العاملة مثل TF1Plus وM6Plus.

## الإعداد داخل CloudStream

بعد تثبيت الإضافة، افتح:

```text
CloudStream → Settings → Extensions → Telebox / Linkbox → Settings
```

ستظهر نافذة تحتوي على:

- رابط مشاركة Linkbox العام.
- Telebox API Token اختياري.
- Folder ID اختياري.

للاختبار الأول، ضع رابط مشاركة عام فقط واترك Token وFolder ID فارغين، ثم اضغط حفظ. بعد ذلك افتح مصدر Telebox واضغط تحديث؛ ستظهر الملفات التي يعيدها رابط المشاركة.

## البناء

```bash
./gradlew TeleboxProvider:make -x TeleboxProvider:ensureJarCompatibility
```

يتطلب Android SDK 35 وJava 21. يستخدم المشروع stubs CloudStream وAndroidX AppCompat/Fragment حتى يعمل `Plugin.openSettings`، كما تفعل مشاريع CloudStream الحديثة التي تحتوي على إعدادات حساب.

## ملاحظات

واجهات Linkbox المستخدمة غير رسمية وقد تتغير. مسار التشغيل الحالي يعتمد على رابط مباشر يعيده `data.list[].url` من API المشاركة العامة. دعم Token الحساب الخاص موجود في واجهة الإدخال، لكن endpoint تنزيل خاص موثق لم يتم التحقق منه بعد.
