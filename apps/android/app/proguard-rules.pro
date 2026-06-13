# kotlinx.serialization: keep generated serializers and the @Serializable models.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep the @Serializable model classes and their synthetic serializers.
-keep,includedescriptorclasses class app.pact.android.model.**$$serializer { *; }
-keepclassmembers class app.pact.android.model.** {
    *** Companion;
}
-keepclasseswithmembers class app.pact.android.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# OkHttp / Okio: drop benign warnings for absent optional deps.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Firebase messaging is reflectively initialized; keep it intact.
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**
