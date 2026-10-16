plugins {
    id("what.kmp.feature")
}

android {
    namespace = "app.what.features.news"
    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
    }
}
