plugins {
    `kotlin-dsl`
}

group = "app.what.schedule.buildlogic"

dependencies {
    implementation(libs.android.gradlePlugin)
    implementation(libs.kotlin.gradlePlugin)
    implementation(libs.compose.gradlePlugin)
    implementation(libs.compose.compiler.gradlePlugin)
    implementation(libs.kotlin.serialization.plugin)
}

gradlePlugin {
    plugins {
        register("whatKmpLibrary") {
            id = "what.kmp.library"
            implementationClass = "app.what.schedule.buildlogic.WhatKmpLibraryPlugin"
        }
        register("whatKmpCompose") {
            id = "what.kmp.compose"
            implementationClass = "app.what.schedule.buildlogic.WhatKmpComposePlugin"
        }
        register("whatKmpFeature") {
            id = "what.kmp.feature"
            implementationClass = "app.what.schedule.buildlogic.WhatKmpFeaturePlugin"
        }
        register("whatScheduleLib") {
            id = "what.kmp.schedule.lib"
            implementationClass = "app.what.schedule.buildlogic.WhatScheduleLibPlugin"
        }
    }
}
