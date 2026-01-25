// OpenWeather API adapter
// Resources in src/main/resources/db/migration/ will be included in the JAR

plugins {
    `java-library`
}

dependencies {
    // Core API (compile-only to avoid bundling)
    compileOnly(project(":owl-core"))

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
            "Implementation-Title" to "OpenWeather API Adapter",
            "Implementation-Version" to project.version,
            "Adapter-Name" to "openweather",
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
