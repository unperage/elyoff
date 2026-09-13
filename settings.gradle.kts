pluginManagement {
    plugins {
        id("fabric-loom") version extra["loomVersion"] as String
        id("dev.kikugie.loom-back-compat") version "0.4.2"
    }
    repositories {
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
