plugins {
    java
    application
}

group = "io.littlehorse.streamsense"
version = "0.1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

val kafkaVersion = "4.2.0"
val smallryeConfigVersion = "3.16.0"
val micrometerVersion = "1.16.3"
val jacksonVersion = "2.21.0"
val apicurioVersion = "3.1.7"
val lhClientVersion = "0.16.0"
val logbackVersion = "1.5.29"
val junitVersion = "5.14.2"

dependencies {
    // Kafka Streams
    implementation("org.apache.kafka:kafka-streams:$kafkaVersion")

    // SmallRye Config
    implementation("io.smallrye.config:smallrye-config:$smallryeConfigVersion")
    implementation("io.smallrye.config:smallrye-config-source-yaml:$smallryeConfigVersion")

    // Metrics
    implementation("io.micrometer:micrometer-registry-prometheus:$micrometerVersion")

    // LittleHorse Client (protobuf models)
    implementation("io.littlehorse:littlehorse-client:$lhClientVersion")

    // Jackson
    implementation("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")

    // Apicurio Registry SerDe
    implementation("io.apicurio:apicurio-registry-jsonschema-serde-kafka:$apicurioVersion")

    // Logging
    implementation("ch.qos.logback:logback-classic:$logbackVersion")

    // Test
    testImplementation("org.junit.jupiter:junit-jupiter:$junitVersion")
    testImplementation("org.apache.kafka:kafka-streams-test-utils:$kafkaVersion")
    testImplementation("org.testcontainers:testcontainers:1.21.4")
    testImplementation("org.testcontainers:junit-jupiter:1.21.4")
    testImplementation("org.testcontainers:kafka:1.21.4")
    testImplementation("org.apache.kafka:kafka-clients:$kafkaVersion")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass.set("io.littlehorse.streamsense.diffuser.WorkflowDiffuserApp")
}

tasks.test {
    useJUnitPlatform {
        excludeTags("integration")
    }
}

tasks.register<Test>("integrationTest") {
    useJUnitPlatform {
        includeTags("integration")
    }
    description = "Runs integration tests (requires Docker)"
    group = "verification"
    classpath = sourceSets.test.get().runtimeClasspath
    testClassesDirs = sourceSets.test.get().output.classesDirs
}
