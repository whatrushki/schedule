# General Reflection & Annotations
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod

# Native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

-dontwarn java.awt.**
-dontwarn javax.swing.**

# ========================
# Navigation Core Rules
# ========================

# Preserve all navigation classes
-keep class app.what.navigation.core.** { *; }

# SheetProvider
-keep class * implements app.what.navigation.core.SheetProvider { *; }
-keepclassmembers class * implements app.what.navigation.core.SheetProvider {
    public <init>(...);
}

# NavProvider
-keep class * implements app.what.navigation.core.NavProvider { *; }

# NavComponent
-keep class * extends app.what.navigation.core.NavComponent { *; }
-keepclassmembers class * extends app.what.navigation.core.NavComponent {
    public <init>(...);
}

# Kotlin reflection & classnames
-keepattributes RuntimeVisibleAnnotations, Signature, InnerClasses
-keep class kotlin.reflect.** { *; }
-keepnames class app.what.navigation.core.** { *; }

# Jetpack Compose
-keep @androidx.compose.runtime.Composable class *
-keepclassmembers class * {
    @androidx.compose.runtime.Composable *;
}

# Extension functions in navigation
-keepclassmembers class app.what.navigation.core.** {
    public static void register(...);
}

# SheetNavigator and SheetNavGraph
-keep class app.what.navigation.core.SheetNavigator {
    *;
}
-keep class app.what.navigation.core.SheetNavGraph {
    *;
}

# NavGraphBuilder helper
-keep class androidx.navigation.NavGraphBuilder { *; }

# Ksoup
-keep class com.fleeksoft.ksoup.** { *; }
-dontwarn com.fleeksoft.ksoup.**
