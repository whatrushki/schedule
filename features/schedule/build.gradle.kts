plugins {
    id("what.kmp.feature")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.qrose)
            implementation(libs.materialKolor)
        }
    }
}

android {
    namespace = "app.what.features.schedule"
    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
    }
}