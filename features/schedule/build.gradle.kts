plugins {
    id("what.kmp.feature")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.qrose)
            implementation(libs.materialKolor)
            implementation(libs.foundation.delivery)
        }
    }
}

android {
    namespace = "app.what.features.schedule"
    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
    }
}