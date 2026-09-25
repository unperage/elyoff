pluginManagement {
    plugins {
        id("fabric-loom") version extra["loomVersion"] as String
        id("dev.kikugie.loom-back-compat") version "0.4.2"
    }
    repositories {
        // 国内镜像优先，避免走境外源
        maven("https://maven.aliyun.com/repository/gradle-plugin") { name = "Aliyun Gradle Plugin" }
        maven("https://maven.aliyun.com/repository/central") { name = "Aliyun Central" }
        maven {
            name = "Fabric"
            url = uri("https://maven.fabricmc.net/")
        }
        maven("https://maven.kikugie.dev/releases") { name = "KikuGie Releases" }
        maven("https://maven.kikugie.dev/snapshots") { name = "KikuGie Snapshots" }
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = extra["modid"] as String
include("26.2")
include("26.3")
