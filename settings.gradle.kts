pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
    repositories {
        mavenLocal()
        google()
        mavenCentral()
        maven {
            url = uri("https://artifactory-external.vkpartner.ru/artifactory/maven")
        }
        maven {
            url = uri("https://maven.pkg.github.com/whatrushki/compose-foundation")
            credentials {
                username = providers.gradleProperty("gpr.user").orNull
                    ?: System.getenv("GPR_USER")
                    ?: System.getenv("GITHUB_ACTOR")
                    ?: "whatrushki"
                password = providers.gradleProperty("gpr.key").orNull
                    ?: System.getenv("GPR_KEY")
                    ?: System.getenv("KEYSTORE_ACCESS_TOKEN")
                    ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
}

rootProject.name = "Schedule"
include(":app")
include(":composeApp")
include(":domain")
include(":data")
include(":features:main")
include(":features:schedule")
include(":features:settings")
include(":features:news")
include(":features:onboarding")
include(":features:dev")
include(":features:account")

include(":libs:schedule:core")
include(":libs:schedule:rksi")
include(":libs:schedule:dgtu")
include(":libs:schedule:iubip")
include(":libs:schedule:rinh")
include(":libs:schedule:sfedu")

include(":tools:schedule-sync")
include(":desktopApp")
include(":wasmApp")
include(":iosApp")
