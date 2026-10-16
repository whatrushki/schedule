package app.what.schedule.buildlogic

import com.android.build.gradle.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
class WhatKmpLibraryPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        with(pluginManager) {
            apply("org.jetbrains.kotlin.multiplatform")
            apply("com.android.library")
        }

        extensions.configure<KotlinMultiplatformExtension> {
            jvmToolchain(21)

            jvm()
            androidTarget {
                publishLibraryVariants("release")
            }
            iosX64()
            iosArm64()
            iosSimulatorArm64()

            @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
            wasmJs {
                browser()
            }
        }

        extensions.configure<LibraryExtension> {
            compileSdk = 36

            defaultConfig {
                minSdk = 26
                testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            }

            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_21
                targetCompatibility = JavaVersion.VERSION_21
            }

            buildTypes {
                getByName("debug") {
                    isMinifyEnabled = false
                }
            }
        }
    }
}
