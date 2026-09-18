plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlinx.serialization)
}

kotlin {
    jvmToolchain(21)
}


dependencies {
    implementation(project(":domain"))
    implementation(project(":libs:schedule:core"))
    implementation(project(":libs:schedule:rksi"))
    implementation(project(":libs:schedule:dgtu"))
    implementation(project(":libs:schedule:iubip"))
    implementation(project(":libs:schedule:rinh"))
    implementation(project(":libs:schedule:sfedu"))
    implementation(project(":composeApp"))

    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation(compose.components.resources)

    implementation(libs.bundles.ktor)
    implementation(libs.koin.core)
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.serialization.json)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing")
    implementation("net.java.dev.jna:jna:5.14.0")
    implementation("net.java.dev.jna:jna-platform:5.14.0")
}

compose.desktop {
    application {
        mainClass = "app.what.schedule.desktop.MainKt"
        buildTypes.release.proguard {
            isEnabled.set(false)
        }
        nativeDistributions {
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Exe,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Rpm
            )
            packageName = "WHAT-Schedule"
            packageVersion = "1.3.6"
            windows {
                menu = true
                shortcut = true
                menuGroup = "WHAT-Schedule"
                iconFile.set(project.file("src/main/resources/icons/icon.ico"))
            }
            linux {
                iconFile.set(project.file("src/main/resources/icons/icon.png"))
            }
            macOS {
                iconFile.set(project.file("src/main/resources/icons/icon.png"))
            }
        }
    }
}
