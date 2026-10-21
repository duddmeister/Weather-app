# kotlinx.serialization: keep generated serializers for the app's @Serializable models
# (the library ships its own rules; these cover the app's classes explicitly).
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers @kotlinx.serialization.Serializable class io.github.duddmeister.weather.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class io.github.duddmeister.weather.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class io.github.duddmeister.weather.**$$serializer { *; }

# Glance instantiates widget action callbacks reflectively by class name.
-keep class * implements androidx.glance.appwidget.action.ActionCallback { <init>(); }

# WorkManager instantiates workers reflectively.
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# OkHttp optional platform integrations that aren't on Android.
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
