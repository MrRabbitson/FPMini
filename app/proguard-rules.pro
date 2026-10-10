# Jsoup использует рефлексию минимально, но держим его целиком.
-keep class org.jsoup.** { *; }
-dontwarn org.jspecify.annotations.**
-dontwarn com.google.re2j.**
# Модели разбора
-keep class app.yarmarka.core.** { *; }
