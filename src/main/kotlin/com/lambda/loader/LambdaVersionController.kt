package com.lambda.loader

import net.fabricmc.loader.impl.FabricLoaderImpl
import java.net.URI
import java.net.URL

/**
 * Version controller for Lambda Client.
 * Fetches Lambda Client versions matching the current Minecraft version.
 */
class LambdaVersionController(
    cache: Cache = Cache(),
    private val minecraftVersionOverride: String? = null
) : BaseMavenVersionController(cache, versionMatchingEnabled = true) {

    override val mavenUrl: String = "https://maven.lambda-client.org"
    override val stableMetaUrl: URL = URI("https://maven.lambda-client.org/releases/com/lambda/lambda/maven-metadata.xml").toURL()
    override val snapshotMetaUrl: URL = URI("https://maven.lambda-client.org/snapshots/com/lambda/lambda/maven-metadata.xml").toURL()
    override val artifactPath: String = "com/lambda/lambda"
    override val artifactName: String = "lambda"

    private val minecraftVersion: String by lazy {
        minecraftVersionOverride ?: FabricLoaderImpl.INSTANCE.getGameProvider().rawGameVersion
    }

    override fun getVersionToMatch(): String = minecraftVersion
}
