# Motrix Android ProGuard Rules

# Keep Aria2 engine models (used in JSON serialization)
-keepclassmembers class com.motrix.android.core.engine.model.** {
    <fields>;
}

# Keep domain models
-keepclassmembers class com.motrix.android.domain.model.** {
    <fields>;
}

# Keep Room entities
-keep class com.motrix.android.core.database.entity.** { *; }

# Keep kotlinx.serialization annotated classes
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
    *** serializers;
}

# Timber
-dontwarn org.jetbrains.annotations.**

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
