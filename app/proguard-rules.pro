# Add project specific ProGuard rules here.
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep line numbers for readable crash stack traces, hide original source file names.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ── kotlinx.serialization ───────────────────────────────────────────────────
# Keep generated serializers and the @Serializable models they reference.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
# Vault model classes are (de)serialized reflectively via their companion serializer.
-keep,includedescriptorclasses class com.github.aashishvibhu.credentialmanagement.domain.model.** { *; }
-keepclassmembers class com.github.aashishvibhu.credentialmanagement.domain.model.** {
    *** Companion;
}
-keepclasseswithmembers class com.github.aashishvibhu.credentialmanagement.domain.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ── Google Drive SDK / google-api-client (uses Gson + reflection) ───────────
-keep class com.google.api.client.** { *; }
-keep class com.google.api.services.drive.** { *; }
-keep class com.google.gson.** { *; }
# google-http-client references optional classes that are not present at runtime.
-dontwarn com.google.api.client.**
-dontwarn com.google.common.**
-dontwarn org.apache.http.**
-dontwarn org.joda.time.**
-keepclassmembers class * {
    @com.google.api.client.util.Key <fields>;
}

# ── Hilt / Dagger ───────────────────────────────────────────────────────────
# Hilt ships its own consumer rules, but keep the generated components defensively.
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager { *; }

# ── Room ────────────────────────────────────────────────────────────────────
# Room ships consumer rules; keep generated implementations to be safe.
-keep class * extends androidx.room.RoomDatabase { *; }
-dontwarn androidx.room.paging.**

# ── Misc ────────────────────────────────────────────────────────────────────
# Keep Kotlin metadata so reflection-based libraries continue to work.
-keep class kotlin.Metadata { *; }
