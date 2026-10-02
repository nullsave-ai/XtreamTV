# Xtream TV

تطبيق Android TV / Google TV / TV Box لمحتوى Xtream — Kotlin + Jetpack Compose + Media3.
واجهة تعمل بالريموت فقط (D-pad)، مصمَّمة على مقياس نسبي يعطي نفس الشكل على 720p / 1080p / 4K.

## البناء
- AGP 8.7.3 · Kotlin 2.0.21 · Gradle 8.9 · compileSdk 35 · minSdk 23
- لا يوجد gradle-wrapper.jar في المشروع. افتحه في Android Studio أو Code on the Go، أو شغّل `gradle wrapper` مرة واحدة.
- إن كانت أدواتك تدعم إصدارات أقدم/أحدث، عدّل الإصدارات في `build.gradle.kts` و`app/build.gradle.kts` فقط.
- تبعيات خفيفة: Compose (ui + foundation فقط)، Media3 (exoplayer + hls + ui)، Coil، OkHttp. لا Material ولا مكتبات أيقونات ولا Hilt؛ كل الأيقونات مرسومة بـ Canvas.

## البنية
```
com.xtreamtv
├─ core/      UiState, الأخطاء, loadState, التنسيق
├─ domain/    النماذج (Account, LiveChannel, Movie, Series, EpgEntry, Catalog...)
├─ data/      XtreamApi (OkHttp) + XtreamRepository (ذاكرة → قرص → شبكة، وقرص قديم عند انقطاع الاتصال)
│  └─ local/  AccountStore, UserStore (مفضلة/متابعة/مشاهَد), SettingsStore
├─ design/    Theme (ألوان + مقياس u/us), Icons (Canvas), Components (FocusBox, TvButton, TvDialog...)
├─ player/    PlayerFactory, LiveController (zapping), PlayerSurface, TrackOptions
└─ ui/        AppRoot (الشريط الجانبي + Back)، nav/، home/، live/، epg/، catalog/، detail/، player/، search/، accounts/، settings/
```

## نظام التركيز
`FocusBox` هو أساس كل عنصر قابل للتركيز: تكبير بسيط + إطار ذهبي + سطح أفتح. الضغط المطوّل على OK يستدعي `onLongClick` (يُستخدم للمفضلة).

## مفاتيح الريموت في البث المباشر
| المفتاح | الفعل |
|---|---|
| أي سهم / OK (القائمة مخفية) | إظهار قائمة القنوات |
| ↑ ↓ داخل القائمة | تنقّل بين القنوات (دون تغيير البث) |
| OK على قناة | تشغيلها فورًا؛ OK على القناة الجارية يخفي القائمة |
| ← → داخل القائمة | تغيير التصنيف (← التالي، → السابق) |
| OK مطوّلًا | إضافة/إزالة من المفضلة |
| CH+ / CH− / PageUp / PageDown | تبديل مباشر للقناة |
| 0–9 | الانتقال لرقم قناة |
| GUIDE | دليل البرامج (EPG) |
| INFO | شريط معلومات القناة |
| MENU | إظهار/إخفاء القائمة |
| Back | إخفاء القائمة ثم الخروج |

## ملاحظات
- كل بيانات EPG تأتي من السيرفر فقط؛ إن لم توجد تظهر عبارة "لا تتوفر بيانات".
- البحث في الحلقات يشمل المسلسلات التي فُتحت تفاصيلها فقط (الـ API لا يدعم بحث الحلقات).
- كلمات المرور تُحفظ في SharedPreferences عادية (غير مشفّرة).
- خطوط الواجهة هي خط النظام؛ لإضافة خط عربي ضعه في `res/font` واستخدمه في `UText`.
