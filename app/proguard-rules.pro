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
-dontwarn kotlinx.serialization.**
-keep class kotlinx.serialization.** { *; }
-keep interface kotlinx.serialization.** { *; }
-keepclassmembers class * {
    *** Companion;
}
-keepclassmembers class * {
    @kotlinx.serialization.Serializable *;
}
-keep class * implements kotlinx.serialization.KSerializer { *; }
-keep class **$$serializer { *; }
-keepclassmembers class * {
    *** serializer(...);
}

# ========== Jetbrains Kotlin Coroutines & Suspend Functions ==========
-dontwarn kotlinx.coroutines.**
-keep class kotlinx.coroutines.** { *; }
-keep interface kotlinx.coroutines.** { *; }

-keep class kotlin.coroutines.Continuation { *; }
-keep interface kotlin.coroutines.Continuation { *; }
-keep class kotlin.coroutines.jvm.internal.ContinuationImpl { *; }
-keepclassmembers class * extends kotlin.coroutines.jvm.internal.ContinuationImpl {
    *** invokeSuspend(...);
}

# ========== Retrofit 2 & OkHttp3 ==========
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepclassmembers class * {
    @retrofit2.http.** <methods>;
}

# Preserve the generic signatures of Retrofit interface methods
-if interface * { @retrofit2.http.** <methods>; }
-keep,allowobfuscation,allowoptimization,allowshrinking interface <1> {
    <methods>;
}

-dontwarn okhttp3.**
-keep class okhttp3.** { *; }

-dontwarn okio.**
-keep class okio.** { *; }

# ========== Notion API DTO and Interface Rules ==========
# Keep all package components and their exact signatures for Retrofit's reflection to read
-keep class com.notiontasks.app.data.remote.dto.** { *; }
-keepattributes Signature, InnerClasses, EnclosingMethod
-keep interface com.notiontasks.app.data.remote.NotionApi { *; }
-keepclassmembers interface com.notiontasks.app.data.remote.NotionApi {
    <methods>;
}

