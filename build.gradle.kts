import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.4.0"
    id("org.jetbrains.intellij.platform") version "2.18.1"
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

repositories {
    mavenCentral()
    intellijPlatform { defaultRepositories() }
}

dependencies {
    intellijPlatform {
        val installed = providers.gradleProperty("localIdePath").orNull
            ?.takeIf { it.isNotBlank() && file(it).exists() }
        if (installed != null) {
            local(installed)
        } else {
            pycharm(providers.gradleProperty("platformVersion"))
        }

        bundledPlugin("PythonCore")

        testFramework(TestFrameworkType.Platform)
    }
}

kotlin { jvmToolchain(25) }

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

intellijPlatform {
    pluginConfiguration {
        name = "DClass"
        ideaVersion {
            sinceBuild = "262"
            untilBuild = provider { null }
        }
    }
    buildSearchableOptions = false
}
