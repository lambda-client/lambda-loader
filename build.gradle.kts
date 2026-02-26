import java.util.Properties

val modId: String by project
val mavenGroup: String by project
val modVersion: String by project
val minecraftVersion: String by project
val yarnMappings: String by project
val fabricLoaderVersion: String by project
val fabricApiVersion: String by project
val kotlinFabricVersion: String by project

val replacements = file("gradle.properties").inputStream().use { stream ->
    Properties().apply { load(stream) }
}.map { (k, v) -> k.toString() to v.toString() }.toMap()

plugins {
    kotlin("jvm") version "2.3.0"
    id("fabric-loom") version "1.14-SNAPSHOT"
    id("maven-publish")
}

version = project.property("modVersion") as String
group = project.property("mavenGroup") as String
base.archivesName = modId

dependencies {
    // To change the versions see the gradle.properties file
    minecraft("com.mojang:minecraft:${project.property("minecraftVersion")}")
    mappings("net.fabricmc:yarn:${project.property("yarnMappings")}:v2")

    modImplementation("net.fabricmc:fabric-loader:${project.property("fabricLoaderVersion")}")
    modImplementation("net.fabricmc:fabric-language-kotlin:${project.property("kotlinLoaderVersion")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${project.property("fabricApiVersion")}")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks {
    test {
        useJUnitPlatform()
    }

    processResources {
        filesMatching("fabric.mod.json") { expand(replacements) }

        // Forces the task to always run
        outputs.upToDateWhen { false }
    }
}

kotlin.jvmToolchain(21)

java {
    withSourcesJar()

    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

// configure the maven publication
publishing {
    val publishType = project.findProperty("mavenType").toString()
    val isSnapshots = publishType == "snapshots"
    val mavenUrl = if (isSnapshots) "https://maven.lambda-client.org/snapshots" else "https://maven.lambda-client.org/releases"
    val mavenVersion =
        if (isSnapshots) "$modVersion+$minecraftVersion-SNAPSHOT"
        else "$modVersion+$minecraftVersion"

    publications {
        create<MavenPublication>("maven") {
            groupId = mavenGroup
            artifactId = modId
            version = mavenVersion

            from(components["java"])
        }
    }

    repositories {
        maven(mavenUrl) {
            name = "lambda-reposilite"

            credentials {
                username = project.findProperty("mavenUsername").toString()
                password = project.findProperty("mavenPassword").toString()
            }

            authentication {
                create<BasicAuthentication>("basic")
            }
        }
    }
}
