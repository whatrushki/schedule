# ProGuard rules for WHAT-Schedule Desktop

# 1. Mode: Optimization settings
-dontoptimize

# 2. General attributes to preserve for reflection, annotations, and stack traces
-keepattributes *Annotation*, InnerClasses, EnclosingMethod, Signature, Exceptions
-dontwarn **
-ignorewarnings

# 3. JNA (Java Native Access - Win32 API, Dark Window decorations)
-keep class com.sun.jna.** { *; }
-keep class net.java.dev.jna.** { *; }
-keep interface * extends com.sun.jna.Library { *; }
-keep class * extends com.sun.jna.Structure { *; }
-keep class app.what.schedule.desktop.ui.** { *; }

# 4. Koin Dependency Injection
-keep class org.koin.** { *; }

# 5. Ktor HTTP Client (CIO engine, Netty, sun.misc.Unsafe)
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**

# 6. Room & SQLite
-keep class androidx.room.** { *; }
-keep class androidx.sqlite.** { *; }

# 7. Coil Image Loader
-keep class coil3.** { *; }

# 8. App Domain & Data
-keep class app.what.domain.models.** { *; }
-keep class app.what.data.** { *; }

# 9. Enums
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
