plugins {
    base
}

allprojects {
    group = property("projectGroup") as String
    version = property("projectVersion") as String
}

// Configure Java version for all subprojects with java plugin
subprojects {
    plugins.withType<JavaPlugin> {
        extensions.configure<JavaPluginExtension> {
            toolchain {
                languageVersion.set(JavaLanguageVersion.of(23))
            }
        }
    }
}
