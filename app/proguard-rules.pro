# Proguard rules for NotionTasker app

# Google ErrorProne annotations (Tink crypt dependency flags these as missing)
-dontwarn com.google.errorprone.annotations.**

# Other common compile-only annotations referenced by libraries
-dontwarn javax.annotation.**
-dontwarn org.checkerframework.**
-dontwarn org.codehaus.mojo.animalsniffer.**

# Keep essential attributes for Generics, Reflection, and Annotations
-keepattributes Signature, InnerClasses, EnclosingMethod, AnnotationDefault, RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations, RuntimeVisibleTypeAnnotations, *Annotation*

# ========== Kotlinx Serialization ==========
# Library-specific rules are usually bundled, so we only keep rules for our own DTOs/Serializers
-keepclassmembers class * {
    @kotlinx.serialization.Serializable *;
}
-keep class * implements kotlinx.serialization.KSerializer { *; }
-keep class **$$serializer { *; }
-keepclassmembers class * {
    *** serializer(...);
}

# ========== Jetbrains Kotlin Coroutines & Suspend Functions ==========
# Basic rules for Coroutines (broad keeps removed to avoid R8 warnings)
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepnames class kotlinx.coroutines.android.AndroidExceptionPreHandler {}
-keepnames class kotlinx.coroutines.android.AndroidDispatcherFactory {}

-keep class kotlin.coroutines.Continuation { *; }
-keep interface kotlin.coroutines.Continuation { *; }
-keep class kotlin.coroutines.jvm.internal.ContinuationImpl { *; }
-keepclassmembers class * extends kotlin.coroutines.jvm.internal.ContinuationImpl {
    *** invokeSuspend(...);
}

# ========== Retrofit 2 & OkHttp3 ==========
# Retrofit 2 rules: Keep annotations and method signatures
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers interface * {
    @retrofit2.http.** <methods>;
}

# Preserve the generic signatures of Retrofit interface methods
-if interface * { @retrofit2.http.** <methods>; }
-keep,allowobfuscation,allowoptimization,allowshrinking interface <1> {
    <methods>;
}

# OkHttp3: Broad keeps removed as library includes its own rules
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**

# ========== Notion API DTO and Interface Rules ==========
# Keep all package components and their exact signatures for Retrofit's reflection to read
-keep class com.notiontasks.app.data.remote.dto.** { *; }
-keep interface com.notiontasks.app.data.remote.NotionApi { *; }
-keepclassmembers interface com.notiontasks.app.data.remote.NotionApi {
    <methods>;
}
