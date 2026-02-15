plugins {
    id("io.micronaut.application")
    id("com.gradleup.shadow")
    id("io.micronaut.aot")
}

dependencies {
    // Annotation processors
    annotationProcessor("io.micronaut.data:micronaut-data-processor")
    annotationProcessor("io.micronaut:micronaut-http-validation")
    annotationProcessor("io.micronaut.serde:micronaut-serde-processor")

    // Micronaut
    implementation("io.micronaut:micronaut-http-client")
    implementation("io.micronaut:micronaut-management")
    implementation("io.micronaut.data:micronaut-data-jdbc")
    implementation("io.micronaut.flyway:micronaut-flyway")
    implementation("io.micronaut.reactor:micronaut-reactor")
    implementation("io.micronaut.reactor:micronaut-reactor-http-client")
    implementation("io.micronaut.serde:micronaut-serde-jackson")
    implementation("io.micronaut:micronaut-jackson-databind")
    implementation("io.micronaut.sql:micronaut-jdbc-hikari")
    implementation("io.micrometer:context-propagation")
    implementation("io.micronaut.micrometer:micronaut-micrometer-core")

    // Database
    runtimeOnly("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    // Logging
    runtimeOnly("ch.qos.logback:logback-classic")
    runtimeOnly("org.yaml:snakeyaml")

    // Adapters and services (discovered by Micronaut DI at runtime)
    runtimeOnly(project(":owl-adapter-davis"))
    runtimeOnly(project(":owl-adapter-openweather"))
    runtimeOnly(project(":owl-adapter-metar"))
    runtimeOnly(project(":owl-adapter-blitzortung"))
    runtimeOnly(project(":owl-adapter-pmsensor"))
    runtimeOnly(project(":owl-adapter-vlinder"))
    runtimeOnly(project(":owl-service-export"))

    // Testing
    testRuntimeOnly("com.h2database:h2")
}

application {
    mainClass.set("com.owl.core.OwlApplication")
    applicationDefaultJvmArgs = listOf(
        // Enable native access for jSerialComm without warnings
        "--enable-native-access=ALL-UNNAMED",
        // Use project-local temp directory to avoid antivirus issues with DLL extraction
        "-Djava.io.tmpdir=${project.rootDir}/tmp"
    )
}

graalvmNative.toolchainDetection = false

micronaut {
    runtime("netty")
    testRuntime("junit5")
    processing {
        incremental(true)
        annotations("com.owl.core.*")
    }
    aot {
        optimizeServiceLoading = true
        convertYamlToJava = false
        precomputeOperations = true
        cacheEnvironment = true
        optimizeClassLoading = true
        deduceEnvironment = true
        optimizeNetty = true
        replaceLogbackXml = true
    }
}


tasks.named<io.micronaut.gradle.docker.NativeImageDockerfile>("dockerfileNative") {
    jdkVersion.set("21")
}
