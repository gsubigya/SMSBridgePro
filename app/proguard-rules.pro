-keep class io.ktor.** { *; }
-keep class io.netty.** { *; }
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keep,includedescriptorclasses class com.smsbridgepro.**$$serializer { *; }
-keepclassmembers class com.smsbridgepro.** { *** Companion; }
-keepclasseswithmembers class com.smsbridgepro.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class com.smsbridgepro.model.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
