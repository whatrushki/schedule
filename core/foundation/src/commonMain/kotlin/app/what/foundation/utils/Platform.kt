package app.what.foundation.utils

enum class PlatformType {
    Android, Jvm, Ios, Wasm;

    val isWeb: Boolean get() = this == Wasm
}

expect val currentPlatform: PlatformType
