// Top-level build file where you can add configuration options common to all sub-projects/modules.
buildscript {
    configurations.configureEach {
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
}

plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.compose.compiler) apply false
  alias(libs.plugins.kotlin.serialization) apply false
}