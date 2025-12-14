plugins {
    kotlin("jvm") version "2.2.20"
    id("org.jetbrains.intellij") version "1.17.3"
}

group = "com.turknet.devops"
version = "1.0.0"

repositories {
    mavenCentral()
}

intellij {
    version.set("2023.3")
    type.set("IC")
    plugins.set(listOf())
    updateSinceUntilBuild.set(false)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.11.0")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("org.yaml:snakeyaml:2.2")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks {
    patchPluginXml {
        sinceBuild.set("233")
        changeNotes.set("""
            <h3>1.0.0 - Initial Release</h3>
            <ul>
                <li>Chat mode for normal conversation with Ollama</li>
                <li>Agent mode for project-wide code analysis</li>
                <li>File mentioning with @ symbol</li>
                <li>Persistent chat history with multiple sessions</li>
                <li>Config file support (YAML/JSON)</li>
                <li>Environment variable support</li>
                <li>IntelliJ Settings UI integration</li>
            </ul>
        """.trimIndent())
    }
}
