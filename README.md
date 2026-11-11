# TeleboxProvider for CloudStream

هذا مستودع مصدر تجريبي لمزود CloudStream لخدمة Telebox/Linkbox. تمت إزالة ملف DEX السابق من قائمة التوزيع لأنه كان مأخوذاً من إضافة GDIndex وليس بناءً خاصاً بهذا المشروع.

## الحالة الحالية

المشروع يحتوي على هيكل CloudStream حديث وكود أولي يستند إلى endpoint `api/open/file_search` المذكور في عميل [TheWNetwork/twn-api-upload](https://github.com/TheWNetwork/twn-api-upload). واجهة Telebox غير موثقة وقد تتغير، كما أن طريقة إدخال token داخل CloudStream لم تُنفذ بعد. لذلك لا توجد حالياً إضافة قابلة للتثبيت عبر `plugins.json`، ولا ينبغي اعتبار المستودع إصداراً يعمل.

## مراجع البنية

- [CS-AR repo.json](https://raw.githubusercontent.com/mo7AmMeD64/cs-ar/main/repo/repo.json) يستخدم `manifestVersion` و`pluginLists`.
- [3rabi repo](https://raw.githubusercontent.com/Abodabodd/re-3arabi/refs/heads/main/repo) يستخدم البنية نفسها مع قائمة إضافات منشورة.
- ملفات `plugins.json` العاملة تشير إلى ملف `.cs3` مبني فعلياً، وتضم `apiVersion` و`fileSize` و`fileHash` عند توفره.

## البناء

```bash
./gradlew TeleboxProvider:make
```

ينشئ GitHub Actions ملف `.cs3` كـ artifact بعد نجاح البناء. لا تتم إضافته تلقائياً إلى `plugins.json` قبل اختبار حقيقي على Android.

## لماذا لا يوجد تثبيت مباشر؟

ملف `.cs3` السابق في Release `v4.0` كان معنونا بأنه يستخدم DEX من GDIndex، ولذلك كان يثبت كإضافة غير مرتبطة أو لا يعمل. نشره كمزود Telebox سيكون مضللاً. يلزم مطور Android/CloudStream لإكمال واجهة الإعدادات، اختبار استجابة API الحقيقية، واستخراج رابط تنزيل/بث موثوق لكل نوع ملف.

## الترخيص

MIT. راجع [LICENSE](LICENSE).
