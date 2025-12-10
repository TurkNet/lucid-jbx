plugins {
    kotlin("jvm") version "2.2.20"
}

group = "com.lucid"
version = "0.1.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.11.0")

    // tests
    testImplementation("org.junit.jupiter:junit-jupiter:5.9.3")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.11.0")
}

// Note: org.jetbrains.intellij plugin is intentionally not applied here so local Gradle builds and
// unit tests can run without requiring the IntelliJ Gradle plugin to be present. To enable
// plugin development tasks (runIde, patchPluginXml), re-add the plugin and the 'intellij' block
// or run Gradle with a special profile that applies it.

tasks {
    // Use JUnit 5 for tests
    withType<Test> {
        useJUnitPlatform()
    }
}
