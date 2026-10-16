plugins {
    id("what.kmp.feature")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":libs:schedule:dgtu"))
            implementation(project(":libs:schedule:rksi"))
            implementation(project(":libs:schedule:sfedu"))
            implementation(project(":libs:schedule:core"))
            implementation(project(":features:schedule"))
            implementation(project(":features:news"))
            implementation(libs.qrose)
        }
    }
}

android {
    namespace = "app.what.features.account"
}
