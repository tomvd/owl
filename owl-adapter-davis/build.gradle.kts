plugins {
    `java-library`
}

dependencies {
    // Core API (compile-only to avoid bundling)
    compileOnly(project(":owl-core"))

    // Serial communication
    implementation("com.fazecast:jSerialComm:2.11.0")

    // Testing
    testImplementation(project(":owl-core"))
}

tasks.jar {
    manifest {
        attributes(
            "Implementation-Title" to "Davis Vantage Pro Adapter",
            "Implementation-Version" to project.version,
            "Adapter-Name" to "davis-serial",
            "Adapter-Version" to "1.0.0"
        )
    }
}

// Task to package adapter as standalone JAR
tasks.register<Jar>("adapterJar") {
    archiveClassifier.set("standalone")

    from(sourceSets.main.get().output)

    // Bundle dependencies (except core API)
    from(configurations.runtimeClasspath.get().filter {
        !it.name.contains("owl-core")
    }.map { if (it.isDirectory) it else zipTree(it) })

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

artifacts {
    archives(tasks.named("adapterJar"))
}
