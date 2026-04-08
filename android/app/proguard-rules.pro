# Flutter
-keep class io.flutter.app.** { *; }
-keep class io.flutter.plugin.** { *; }
-keep class io.flutter.util.** { *; }
-keep class io.flutter.view.** { *; }
-keep class io.flutter.** { *; }
-keep class io.flutter.plugins.** { *; }

# Kotlin
-dontwarn kotlin.**
-keep class kotlin.** { *; }

# Native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# SharedPreferences
-keep class androidx.datastore.** { *; }

# sqflite
-keep class com.tekartik.sqflite.** { *; }

# flutter_mpv native bindings
-keep class com.mohammed.** { *; }

# Chaquopy bridge objects accessed from Python
-keep class com.example.mpx.CancelToken { *; }
-keep class com.example.mpx.MainActivity$ProgressEmitter { *; }

# Google Play Core (not used, but referenced by Flutter)
-dontwarn com.google.android.play.core.**
-keep class com.google.android.play.core.** { *; }
