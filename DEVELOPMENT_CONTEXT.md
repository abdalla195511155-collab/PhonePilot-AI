# DEVELOPMENT_CONTEXT.md — PhonePilot AI

## قاعدة دائمة: Prohibited local Gradle builds

المحاولة المتكررة للبناء المحلي عبر Gradle تفشل بسبب مشكلة بيئية معروفة على Linux ARM64:
`Failed to load native library 'libnative-platform.so'` و `Could not create Jansi native library`.
هذه مشكلة في بيئة Android Harness، **ليست مشكلة في كود المشروع**.

- لا تُجرى أي محاولة لبناء Gradle محلي (`./gradlew`, `gradle ...`).
- لا تُعدل كود Kotlin/Compose/Gradle/dependencies لمحاولة إصلاح هذه المشكلة البيئية.
- لا تُعامل فشل البيئة كخطأ ترجمة في التطبيق.
- البناء الفعلي، الترجمة، الاختبارات وإنشاء APK يتم عبر **GitHub Actions** فقط.
- إذا أبلغ CI عن خطأ حقيقي في الكود، يتم فحص الخطأ الدقيق وإصلاح الكود المسؤول فقط، دون تعديل شاشات عاملة غير مرتبطة.

## تقدم Phase 1 (UI Professional) — حالة المراجعة النهائية

### ما تم إنجازه
- 9 شاشات Compose احترافية منفصلة في ملفات خاصة:
  1. DashboardScreen
  2. TrainingStudioScreen
  3. SkillLibraryScreen
  4. AgentControlScreen
  5. ExecutionMonitorScreen
  6. AiModelsApiSettingsScreen
  7. LogsHistoryScreen
  8. PrivacyPermissionsScreen
  9. AiAssistantScreen
- نظام تصميم موحد (Color, Type, Space, PhonePilotTheme) مع دعم الفاتح/المظلم.
- مكونات قابلة لإعادة الاستخدام (AppScaffold, PrimaryButton, SecondaryButton, StatCard, EmptyState, LoadingState, ErrorState, ScreenHeader, AppNavigationBar).
- نظام تنقل (Routes, NavigationHost) يربط جميع 9 شاشات.
- دعم RTL عبر ملف `res/values-ar/strings.xml` و `android:supportsRtl="true"` في المانيفست.
- تحديث MainActivity لاستخدام Compose مع الحفاظ على سلوك التسجيل واسترجاع التسجيلات المقاطعة.
- لا تغييرات على RecordingRepository, RecordingState, TaskDatabaseHelper, PrivacyFilter أو AccessibilityService (باستثناء الإصلاحات السابقة للأخطاء البرمجية).

###依赖يات جديدة مبررة
- `navigation-compose` (2.7.7) للتنقل بين الشاشات.
- `compose-material-icons-extended` (بلا إصدار صريح — يديره Compose BOM) للأيقونات. سبب الضرورة: الشاشات تستخدم أيقونات غير موجودة في material-icons-core (مثل SmartToy, MonitorHeart, Tune, School, History, Shield, Automation, DragHandle, TouchApp, LibraryAdd, Key).

### تعديلات نفذت في المراجعة النهائية
- أزال `org.gradle.native=false` من `gradle.properties` (محاولة بيئية غير لازمة، لا أثر له على الكود).
- أصلح مرجع Dependcy في `build.gradle.kts`: كان `libs.material.icons.core` بلا تعريف في `[libraries]`، فأصبح `libs.compose.material.icons.extended` مع إضافة تعريف الـ alias في `[libraries]`. تم تجنب alias باسم `material-icons-core` لأنه يتعارض كبادئة مع alias الموجود `material`.
- أضف تعريف `navigation-compose` في `[libraries]` (كان الإصدار مضافاً في `[versions]` فقط دون تعريف المكتبة).
- `git diff --check`: نظيف.

### حالة البناء والتحقق
- التビル المحلي عبر Gradle: غير ممكن (مشكلة بيئية ARM64). لا تُجرى محاولات محلية.
- GitHub Actions: PENDING (لم يتم تشغيله بعد على التغييرات الحالية).
- لم يتم commit أو push.

## الملفات الجديدة
- ui/theme/Color.kt, Type.kt, Space.kt, PhonePilotTheme.kt
- ui/components/AppScaffold.kt, PrimaryButton.kt, SecondaryButton.kt, StatCard.kt, EmptyState.kt, LoadingState.kt, ErrorState.kt, ScreenHeader.kt, AppNavigationBar.kt (9 ملفات)
- ui/navigation/Routes.kt, NavigationHost.kt
- ui/screens/*Screen.kt (9 ملفات)
- res/values-ar/strings.xml

## الملفات المعدلة
- app/build.gradle.kts (إضافة dependency التنقل والأيقونات)
- app/src/main/java/com/phonepilot/app/ui/MainActivity.kt (إعادة كتابة Compose)
- app/src/main/java/com/phonepilot/app/repository/RecordingRepository.kt (إصلاح when + trySend — إصلاح سابق للأخطاء البرمجية)
- app/gradle/libs.versions.toml (إضافة navigation-compose و compose-material-icons-extended في [versions] و [libraries])

## ملخص الفحص النهائي
- Git status: 4 ملفات معدلة + 5 مجلدات جديدة + DEVELOPMENT_CONTEXT.md
- git diff --check: نظيف
- 점'entrée: ComponentActivity مع setContent و PhonePilotTheme
- التنقل: 9 Routes في Routes.kt، كل 9 مستخدمة في NavigationHost، لا Routes غير مرتبطة
- الحفاظ على الوظائف: markInterruptedRecordings() و isServiceEnabled محفوظان في MainActivity
- Review compile-oriented: استيرادات وش signatures و مراجع الموارد جميعها سليمة

## الخطوة التالية الموصى بها
تشغيل GitHub Actions للتحقق من الترجمة والاختبارات وإنشاء APK debug.
