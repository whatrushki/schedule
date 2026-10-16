plugins {
    id("what.kmp.feature")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":features:schedule"))
            api(project(":features:news"))
            api(project(":features:settings"))
            api(project(":features:onboarding"))
            api(project(":features:account"))
            api(project(":features:dev"))
        }
    }
}

android {
    namespace = "app.what.features.main"
}