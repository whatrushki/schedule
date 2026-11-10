plugins {
    id("what.kmp.feature")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.foundation.delivery)
            implementation(libs.foundation.auth)
            implementation(project(":features:onboarding"))
        }
    }
}

android {
    namespace = "app.what.features.settings"
    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
    }
}