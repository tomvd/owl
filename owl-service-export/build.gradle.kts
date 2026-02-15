plugins {
    id("io.micronaut.library")
}

dependencies {
    annotationProcessor("io.micronaut:micronaut-inject-java")

    implementation(project(":owl-core"))

    // JSON serialization
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")

    // Testing
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.assertj:assertj-core:3.25.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

micronaut {
    processing {
        annotations("com.owl.service.export.*")
    }
}
