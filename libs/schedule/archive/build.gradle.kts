plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlinx.serialization)
}

group = "app.what.schedule"
version = libs.versions.appVersion.get()

// ВНИМАНИЕ: Это архивный модуль старых парсеров вузов.
// Он НЕ должен подключаться в продакшн-модули (app, data, features).
// Сохранен исключительно для исторической справки и обратной совместимости.

kotlin {
    jvmToolchain(21)

    jvm()
    androidTarget()

    sourceSets {
        commonMain.dependencies {
            implementation(project(":libs:schedule:core"))
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
            implementation(libs.ktor.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.json)
            implementation(libs.ksoup.lite)
        }
    }
}

android {
    namespace = "app.what.schedule.archive"
    compileSdk = 36
    defaultConfig {
        minSdk = 26
    }
}
