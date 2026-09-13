plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    maven("https://maven.fabricmc.net/")
    maven("https://maven.kikugie.dev/releases")
    maven("https://maven.kikugie.dev/snapshots")
}

dependencies {
    implementation("net.fabricmc:fabric-loom:1.17-SNAPSHOT")
    implementation("dev.kikugie:loom-back-compat:0.4.2")
}

gradlePlugin {
    plugins {
        create("fabricConventions") {
            id = "fabric-conventions"
            implementationClass = "FabricConventionsPlugin"
        }
    }
}