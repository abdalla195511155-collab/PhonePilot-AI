# تصميم قاعدة البيانات - PhonePilot AI

## الجداول المقترحة

### tasks
| العمود | النوع | الوصف |
|---|---|---|
| id | INTEGER PK | معرف المهمة |
| name | TEXT | اسم المهمة |
| description | TEXT | وصف المهمة |
| created_at | DATETIME | تاريخ الإنشاء |
| status | TEXT | idle, recording, learning, ready, executing, completed, failed |

### task_recordings
| العمود | النوع | الوصف |
|---|---|---|
| id | INTEGER PK | معرف التسجيل |
| task_id | INTEGER FK | المهمة المرتبطة |
| iteration_number | INTEGER | رقم التكرار (1-10) |
| recorded_at | DATETIME | تاريخ التسجيل |
| raw_events | TEXT | JSON للأحداث المسجلة |

## ملاحظات
- لا تُخزن كلمات المرور أو الرموز السرية في أي جدول.
- جميع البيانات حساسة ومحلية على الجهاز فقط.
- مسارات لقطات الشاشة تُحفظ محليًا للتشخيص فقط ولا تُرسل لأي نموذج.
