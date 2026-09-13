val mavenGroup: String by project

allprojects {
    group = mavenGroup
}

subprojects {
    afterEvaluate {
        // Only configure if fabric-loom plugin is applied
        plugins.findPlugin("fabric-loom")?.let {
            // remapJar/remapSourcesJar do not exist for unobfuscated MC (26.x)
            tasks.findByName("remapJar")?.let { task ->
                (task as AbstractArchiveTask).archiveBaseName.set(rootProject.name)
            }

            tasks.findByName("remapSourcesJar")?.let { task ->
                (task as AbstractArchiveTask).archiveBaseName.set(rootProject.name)
            }
        }
    }
}