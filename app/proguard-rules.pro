# Room / مدل‌های داده
-keep class ir.kaveh.yaddashtyar.data.** { *; }
-keepattributes *Annotation*, Signature
-dontwarn org.jetbrains.annotations.**

# استخراج متن PDF
-keep class com.tom_roush.** { *; }
-dontwarn com.tom_roush.**
-dontwarn com.gemalto.jp2.**
-dontwarn org.bouncycastle.**
