plugins {
    `java-library`
}

dependencies {
    // Core API (compile-only to avoid bundling)
    compileOnly(project(":owl-core"))

    // HTTP client is built into Java 11+, no additional dependencies needed

    // Logging (provided by runtime, compile-only here)
    compileOnly("org.slf4j:slf4j-api:2.0.9")

    // Testing
    testImplementation(project(":owl-core"))
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}

tasks.jar {
    manifest {
        attributes(
            "Implementation-Title" to "METAR Weather Adapter",
            "Implementation-Version" to project.version,
            "Adapter-Name" to "metar-http",
            "Adapter-Version" to "1.0.0"
        )
    }
}

// Task to package adapter as standalone JAR
tasks.register<Jar>("adapterJar") {
    archiveClassifier.set("standalone")
    from(sourceSets.main.get().output)
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

artifacts {
    archives(tasks.named("adapterJar"))
}
