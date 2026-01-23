plugins {
    `java-library`
}

dependencies {
    // Core API (compile-only to avoid bundling)
    compileOnly(project(":owl-core"))

    // Logging API (provided at runtime by owl-core)
    compileOnly("org.slf4j:slf4j-api:2.0.9")

    // Testing
    testImplementation(project(":owl-core"))
}

tasks.jar {
    manifest {
        attributes(
            "Implementation-Title" to "Data Export Service",
            "Implementation-Version" to project.version,
            "Service-Name" to "export",
            "Service-Version" to "1.0.0"
        )
    }
}

// Task to package service as standalone JAR
tasks.register<Jar>("serviceJar") {
    archiveClassifier.set("standalone")
    from(sourceSets.main.get().output)
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

artifacts {
    archives(tasks.named("serviceJar"))
}
