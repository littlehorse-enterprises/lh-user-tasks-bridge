plugins {
    java
    application
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.littlehorse:littlehorse-client:0.16.0")
    implementation("org.slf4j:slf4j-api:2.0.17")
    implementation("org.apache.logging.log4j:log4j-slf4j2-impl:2.25.3")
    implementation("org.apache.commons:commons-lang3:3.20.0")
}

application {
    mainClass.set("io.littlehorse.examples.ExampleRunner")
}
