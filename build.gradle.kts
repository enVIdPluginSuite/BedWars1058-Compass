import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.2.21"
    id("com.gradleup.shadow") version "8.3.8"
}

group = "dev.etran"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    maven("https://jitpack.io") {
        // The multi-module POM on JitPack has an unresolvable parent; the jar itself is fine.
        metadataSources { artifact() }
        content { includeGroup("com.github.andrei1058.BedWars1058") }
    }
}

dependencies {
    // Compiled against the 1.8.8 API so the same jar loads on every version BedWars1058 supports (1.8 -> latest).
    compileOnly("org.spigotmc:spigot-api:1.8.8-R0.1-SNAPSHOT") {
        exclude(group = "net.md-5") // bungeecord-chat snapshot no longer exists and isn't used
    }
    compileOnly("com.github.andrei1058.BedWars1058:bedwars-api:24.9")
    implementation(kotlin("stdlib"))
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_1_8)
    }
}

tasks {
    processResources {
        val props = mapOf("version" to project.version)
        inputs.properties(props)
        filesMatching("plugin.yml") { expand(props) }
    }

    shadowJar {
        archiveClassifier.set("")
        // Relocate Kotlin so we never clash with another plugin that also bundles it.
        relocate("kotlin", "dev.etran.bedwars1058compass.libs.kotlin")
        relocate("org.jetbrains", "dev.etran.bedwars1058compass.libs.jetbrains")
        relocate("org.intellij", "dev.etran.bedwars1058compass.libs.intellij")
    }

    jar { enabled = false }
    build { dependsOn(shadowJar) }
}
