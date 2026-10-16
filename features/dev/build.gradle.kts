plugins {
    id("what.kmp.feature")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.foundation.healthcheck)
            implementation(libs.bundles.ktor)
        }
    }
}

android {
    namespace = "app.what.features.dev"
}
