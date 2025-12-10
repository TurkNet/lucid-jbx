plugins {
    kotlin("jvm") version "2.2.20"
}

group = "com.lucid"
version = "0.1.0-core"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.11.0")

    testImplementation("org.junit.jupiter:junit-jupiter:5.9.3")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.11.0")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

