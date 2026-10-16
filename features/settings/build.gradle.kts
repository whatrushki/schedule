plugins {
    id("what.kmp.feature")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
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