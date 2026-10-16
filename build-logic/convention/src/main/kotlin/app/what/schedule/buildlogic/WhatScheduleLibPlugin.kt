package app.what.schedule.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.project
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import java.net.URI

class WhatScheduleLibPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        apply<WhatKmpLibraryPlugin>()

        with(pluginManager) {
            apply("org.jetbrains.kotlin.plugin.serialization")
            apply("maven-publish")
        }

        val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
        group = "app.what.schedule"
        version = libs.findVersion("appVersion").get().requiredVersion

        extensions.configure<KotlinMultiplatformExtension> {
            sourceSets.named("commonMain") {
                dependencies {
                    api(project(":libs:schedule:core"))
                    implementation(libs.findLibrary("kotlinx-serialization-json").get())
                    implementation(libs.findLibrary("kotlinx-datetime").get())
                    implementation(libs.findLibrary("ktor-core").get())
                    implementation(libs.findLibrary("ksoup-lite").get())
                }
            }

            sourceSets.named("jvmMain") {
                dependencies {
                    implementation(libs.findLibrary("ktor-client-cio").get())
                }
            }

            sourceSets.named("commonTest") {
                dependencies {
                    implementation(kotlin("test"))
                }
            }
        }

        extensions.configure<PublishingExtension> {
            repositories {
                maven {
                    name = "GitHubPackages"
                    url = URI.create("https://maven.pkg.github.com/whatrushki/schedule")
                    credentials {
                        username = System.getenv("GITHUB_ACTOR") ?: ""
                        password = System.getenv("GITHUB_TOKEN") ?: ""
                    }
                }
            }
        }
    }
}
