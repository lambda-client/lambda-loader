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
}.map { (k, v) -> k.toString() to v.toString() }.toMap().toMutableMap()

plugins {
    kotlin("jvm") version "2.3.0"
    id("fabric-loom") version "1.14-SNAPSHOT"
    id("maven-publish")
}

val mavenType = project.findProperty("mavenType")?.toString() ?: "dev"
val isDevBuild = mavenType == "dev" || mavenType == "snapshots"
val buildNum = if (project.hasProperty("buildNumber")) project.property("buildNumber") as String else null

version = if (isDevBuild && buildNum != null) {
    "${project.property("modVersion")}-dev.$buildNum"
} else {
    project.property("modVersion") as String
}
replacements["modVersion"] = version.toString()
group = project.property("mavenGroup") as String
base.archivesName = modId

dependencies {
    // To change the versions see the gradle.properties file
    minecraft("com.mojang:minecraft:$minecraftVersion")
    mappings("net.fabricmc:yarn:$yarnMappings:v2")

    modImplementation("net.fabricmc:fabric-loader:$fabricLoaderVersion")
    modImplementation("net.fabricmc:fabric-language-kotlin:$kotlinFabricVersion")
    modImplementation("net.fabricmc.fabric-api:fabric-api:$fabricApiVersion")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks {
    test {
        useJUnitPlatform()
    }

    processResources {
        inputs.properties(replacements)
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

publishing {
    val mavenType = project.findProperty("mavenType").toString()
    val mavenUrl = "https://maven.lambda-client.org/${mavenType}"
    val versionBase = "$modVersion+$minecraftVersion"

    val finalVersion =
        if (mavenType == "dev") {
            val buildNumber = project.findProperty("buildNumber").toString()
            "$versionBase-dev.$buildNumber"
        } else versionBase

    publications {
        create<MavenPublication>("maven") {
            groupId = mavenGroup
            artifactId = modId
            version = finalVersion

            from(components["java"])
        }
    }

    repositories {
        maven(mavenUrl) {
            name = "lambda-maven"

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