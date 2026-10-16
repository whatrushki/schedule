package app.what.schedule.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.project
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

class WhatKmpFeaturePlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        apply<WhatKmpComposePlugin>()

        with(pluginManager) {
            apply("org.jetbrains.kotlin.plugin.serialization")
        }

        val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

        extensions.configure<KotlinMultiplatformExtension> {
            sourceSets.named("commonMain") {
                dependencies {
                    implementation(project(":domain"))
                    implementation(project(":data"))
                    api(libs.findLibrary("compose-foundation").get())
                    api(libs.findLibrary("foundation-navigation").get())

                    implementation(libs.findLibrary("koin-core").get())
                    implementation(libs.findLibrary("koin-compose").get())
                    implementation(libs.findLibrary("kotlinx-serialization-json").get())
                    implementation(libs.findLibrary("kotlinx-datetime").get())
                    implementation(libs.findBundle("coil").get())
                }
            }

            sourceSets.named("androidMain") {
                dependencies {
                    implementation(libs.findLibrary("androidx-core-ktx").get())
                }
            }

            sourceSets.named("commonTest") {
                dependencies {
                    implementation(kotlin("test"))
                }
            }
        }
    }
}
