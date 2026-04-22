# 📄 DocScanner - تطبيق مسح المستندات

تطبيق Android مشابه لـ CamScanner مبني بـ **Kotlin** يتيح التقاط المستندات وتحويلها إلى PDF.

---

## ✨ الميزات

| الميزة | الوصف |
|--------|-------|
| 📸 تصوير متعدد | التقاط عدة صور لمستندات مختلفة |
| 🔲 اقتصاص تلقائي | كشف حواف المستند باستخدام Sobel Edge Detection |
| ✨ تحسين تلقائي | زيادة التباين والحدة لجعل النص أوضح |
| 📄 إنشاء PDF | دمج الصور في ملف PDF مضغوط |
| 📤 مشاركة | مشاركة الـ PDF عبر أي تطبيق |

---

## 🗂️ هيكل الملفات

```
DocScanner/
├── app/
│   ├── build.gradle                          ← المكتبات والإعدادات
│   └── src/main/
│       ├── AndroidManifest.xml               ← الصلاحيات والشاشات
│       ├── java/com/docscanner/
│       │   ├── MainActivity.kt               ← الشاشة الرئيسية
│       │   ├── CameraActivity.kt             ← شاشة الكاميرا (CameraX)
│       │   ├── PreviewActivity.kt            ← شاشة المعاينة + PDF
│       │   ├── ImageProcessor.kt             ← معالجة الصور
│       │   ├── PdfGenerator.kt               ← إنشاء PDF بـ iText7
│       │   ├── ScannedImageAdapter.kt        ← Adapter الشاشة الرئيسية
│       │   └── PreviewAdapter.kt             ← Adapter المعاينة
│       └── res/
│           ├── layout/
│           │   ├── activity_main.xml
│           │   ├── activity_camera.xml
│           │   ├── activity_preview.xml
│           │   ├── item_scanned_image.xml
│           │   └── item_preview_image.xml
│           ├── drawable/                     ← الأيقونات والأشكال
│           ├── values/                       ← الألوان والنصوص والسمة
│           └── xml/
│               └── file_paths.xml            ← إعداد FileProvider
├── build.gradle
└── settings.gradle
```

---

## 🚀 خطوات الإعداد والتشغيل

### المتطلبات
- Android Studio Hedgehog أو أحدث
- JDK 17
- جهاز Android حقيقي (الكاميرا لا تعمل جيداً على المحاكي)

### الخطوات

**1. افتح المشروع في Android Studio:**
```
File → Open → اختر مجلد DocScanner
```

**2. انتظر مزامنة Gradle:**
- Android Studio سيحمّل جميع المكتبات تلقائياً
- قد يستغرق ذلك 2-5 دقائق في المرة الأولى

**3. أضف ExifInterface إلى build.gradle (مطلوب لـ ImageProcessor):**
```groovy
// أضف هذا السطر في dependencies داخل app/build.gradle
implementation 'androidx.exifinterface:exifinterface:1.3.6'
```

**4. شغّل التطبيق:**
```
Run → Run 'app'  أو اضغط Shift+F10
```

---

## 📱 تدفق استخدام التطبيق

```
المستخدم يفتح التطبيق
        ↓
يضغط "التقاط صورة" → يُفتح شاشة الكاميرا
        ↓
يصوّر المستند → الصورة تُعاد للشاشة الرئيسية
        ↓
يكرر التصوير بقدر الحاجة
        ↓
يضغط "إنشاء PDF" → يُفتح شاشة المعاينة
        ↓
تُعالَج الصور تلقائياً (تحسين + اقتصاص)
        ↓
يضغط "إنشاء PDF" → يُنشأ ملف PDF
        ↓
يختار "مشاركة" أو "فتح"
```

---

## 🧠 شرح المكتبات المستخدمة

### CameraX
```kotlin
// المكتبة الرسمية من Google للكاميرا
// سهلة الاستخدام وتدعم جميع أجهزة Android
implementation 'androidx.camera:camera-camera2:1.3.1'
implementation 'androidx.camera:camera-lifecycle:1.3.1'
implementation 'androidx.camera:camera-view:1.3.1'
```

**لماذا CameraX وليس Camera2 مباشرة؟**
- تجريد عالي المستوى → كود أبسط
- تعمل على 90%+ من أجهزة Android
- تدير دورة الحياة تلقائياً

---

### iText7
```kotlin
// مكتبة إنشاء PDF
implementation 'com.itextpdf:itext7-core:7.2.5'
```

**الاستخدام الأساسي:**
```kotlin
val pdfWriter = PdfWriter("output.pdf")
val pdfDoc = PdfDocument(pdfWriter)
val document = Document(pdfDoc)

// إضافة صورة
val imageData = ImageDataFactory.create(imageBytes)
val image = Image(imageData)
image.scaleToFit(PageSize.A4.width, PageSize.A4.height)
document.add(image)

document.close()
```

---

### Glide
```kotlin
// مكتبة تحميل وعرض الصور بكفاءة
implementation 'com.github.bumptech.glide:glide:4.16.0'
```

**الاستخدام:**
```kotlin
Glide.with(context)
    .load(File(imagePath))
    .centerCrop()
    .placeholder(R.drawable.ic_placeholder)
    .into(imageView)
```

---

## 🔧 شرح خوارزميات معالجة الصور

### 1. اقتصاص المستند (Auto-Crop)

```
الصورة الأصلية
      ↓
تحويل لرمادي (Grayscale)
      ↓
كشف الحواف بـ Sobel Operator
      ↓
إيجاد أبعاد المستند (min/max X, Y)
      ↓
اقتصاص مع هامش 2%
```

**Sobel Operator** هو مرشح رياضي يكشف التغيرات المفاجئة في الإضاءة (= الحواف):
```kotlin
// حساب التدرج الأفقي والرأسي
val gx = gray[i + 1] - gray[i - 1]   // أفقي
val gy = gray[i + width] - gray[i - width]  // رأسي
val edgeStrength = sqrt(gx² + gy²)
```

### 2. تحسين الصورة (Enhancement)

**أ. ColorMatrix - تحسين التباين:**
```kotlin
val contrast = 1.3f    // زيادة 30%
val brightness = -20f  // تعتيم طفيف لإبراز النص

ColorMatrix(floatArrayOf(
    contrast, 0, 0, 0, brightness,  // Red
    0, contrast, 0, 0, brightness,  // Green
    0, 0, contrast, 0, brightness,  // Blue
    0, 0, 0, 1, 0                   // Alpha
))
```

**ب. Sharpening Kernel - تحسين الحدة:**
```
النواة المستخدمة:
 0  -1   0
-1   5  -1
 0  -1   0

كيف تعمل: تضاعف قيمة البكسل المركزي × 5
وتطرح منه قيم الجيران → يبرز الحواف والتفاصيل الدقيقة
```

---

## ⚠️ مشاكل شائعة وحلولها

| المشكلة | السبب | الحل |
|---------|-------|------|
| `Gradle sync failed` | مشكلة في الإنترنت أو الإصدارات | `File → Invalidate Caches → Restart` |
| الكاميرا لا تفتح | نقص الصلاحية | تأكد من قبول صلاحية الكاميرا |
| `ClassNotFoundException: iTextPDF` | الحجم الكبير للمكتبة | أضف `multiDexEnabled true` في build.gradle |
| الـ PDF فارغ | مسار الصورة خاطئ | تحقق من صلاحيات التخزين |

### حل مشكلة MultiDex
```groovy
// في app/build.gradle داخل defaultConfig
defaultConfig {
    ...
    multiDexEnabled true
}

// في dependencies
implementation 'androidx.multidex:multidex:2.0.1'
```

---

## 🚀 تحسينات مستقبلية مقترحة

1. **ML Kit Document Scanner** - بديل أحدث لكشف المستندات تلقائياً:
   ```groovy
   implementation 'com.google.mlkit:document-scanner:16.0.0-beta1'
   ```

2. **OCR (تحويل الصورة لنص):**
   ```groovy
   implementation 'com.google.mlkit:text-recognition-arabic:16.0.0'
   ```

3. **ترتيب الصور بالسحب والإفلات** باستخدام `ItemTouchHelper`

4. **اسم مخصص للـ PDF** - إضافة حقل نص قبل الإنشاء

5. **Google Drive Integration** - رفع الـ PDF مباشرة للسحابة

---

## 📞 للاستفسار

هذا المشروع تعليمي للمبتدئين في Android. كل ملف مشروح بالتفصيل داخل الكود بتعليقات عربية.
