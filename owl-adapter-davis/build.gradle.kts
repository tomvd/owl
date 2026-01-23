plugins {
    `java-library`
}

dependencies {
    // Core API (compile-only to avoid bundling)
    compileOnly(project(":owl-core"))

    // Serial communication (2.10.4 has better platform detection than 2.11.0)
    implementation("com.fazecast:jSerialComm:2.10.4")

    // Logging (provided by runtime, compile-only here)
    compileOnly("org.slf4j:slf4j-api:2.0.9")

    // Testing
    testImplementation(project(":owl-core"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.assertj:assertj-core:3.25.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
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
