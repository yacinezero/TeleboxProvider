# TeleboxProvider for CloudStream

مزود Telebox / Linkbox لـ CloudStream.

## الإصدار الحالي

الإصدار v5.3 يقبل روابط المشاركة من:

- `https://lbx.zone/d/...`
- `https://www.linkbox.to/...`
- `https://www.linkbox.zone/a/d/...`

ويعرض المجلدات العامة داخل CloudStream بدلاً من إسقاطها من النتائج. بعد فتح المجلد، تظهر الملفات والمجلدات الموجودة داخله.

## الإعداد

افتح:

```text
CloudStream → Settings → Extensions → Telebox / Linkbox → Settings
```

ضع رابط المشاركة العام في حقل رابط Linkbox ثم اضغط حفظ. يمكن ترك Token وFolder ID فارغين عند اختبار الروابط العامة.

## ملاحظة تشغيل مهمة

واجهة Linkbox العامة تعيد بيانات الملف مثل `item_id` و`vgroup`، ولا تعيد دائماً رابط CDN مباشر. لذلك v5.3 يضمن تصفح المجلدات وعرض الملفات، لكن تشغيل بعض الملفات قد يحتاج endpoint تنزيل/تشغيل خاص بالحساب أو رابطاً مباشراً يعيده API. لا يتم اختلاق رابط فيديو غير صالح.

## البناء

```bash
./gradlew TeleboxProvider:make -x TeleboxProvider:ensureJarCompatibility
```
