plugins {
    kotlin("jvm") version "2.2.20"
}

group = "com.lucid"
version = "0.1.0-plugin"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":core"))
    implementation("com.squareup.okhttp3:okhttp:4.11.0")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

