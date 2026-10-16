pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("androidx.*")
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google {
            content {
                includeGroupByRegex("androidx.*")
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
            }
        }
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "ABlob"
include(":app")

gradle.lifecycle.beforeProject {
    val enforceSafeVersions = Action<org.gradle.api.artifacts.Configuration> {
        resolutionStrategy.eachDependency {
            when (requested.group) {
                "io.netty" -> useVersion("4.1.139.Final")
                "org.bouncycastle" -> if (requested.name in setOf("bcprov-jdk18on", "bcpkix-jdk18on", "bcutil-jdk18on")) {
                    useVersion("1.86")
                }
                "org.jdom" -> if (requested.name == "jdom2") {
                    useVersion("2.0.6.1")
                }
                "org.apache.httpcomponents" -> if (requested.name in setOf("httpclient", "httpmime")) {
                    useVersion("4.5.14")
                }
                "org.apache.commons" -> if (requested.name == "commons-lang3") {
                    useVersion("3.21.0")
                }
                "org.bitbucket.b_c" -> if (requested.name == "jose4j") {
                    useVersion("0.9.7")
                }
                "org.jetbrains.kotlin" -> if (requested.name.startsWith("kotlin-gradle-plugin")) {
                    useVersion("2.4.21")
                }
            }
        }
    }
    buildscript.configurations.configureEach(enforceSafeVersions)
    configurations.configureEach(enforceSafeVersions)
}


