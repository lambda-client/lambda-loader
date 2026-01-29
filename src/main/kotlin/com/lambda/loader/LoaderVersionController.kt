package com.lambda.loader

import com.lambda.loader.config.ConfigManager
import com.lambda.loader.config.ReleaseMode
import net.fabricmc.loader.api.FabricLoader
import java.net.URI
import java.net.URL

/**
 * Version controller for Lambda-Loader self-updates.
 * Fetches loader updates from a separate Maven repository.
 */
class LoaderVersionController(
    cache: Cache = Cache(),
    loaderMavenUrl: String = "https://maven.lambda-client.org"
) : BaseMavenVersionController(cache, versionMatchingEnabled = false) {

    override val mavenUrl: String = loaderMavenUrl
    override val stableMetaUrl: URL = URI("$mavenUrl/releases/com/lambda/loader/maven-metadata.xml").toURL()
    override val snapshotMetaUrl: URL = URI("$mavenUrl/snapshots/com/lambda/loader/maven-metadata.xml").toURL()
    override val artifactPath: String = "com/lambda/loader"
    override val artifactName: String = "loader"

    /**
     * Loader doesn't need version matching - we always want the latest version
     */
    override fun getVersionToMatch(): String? = null
    override fun getReleaseMode(): ReleaseMode = ConfigManager.config.loaderReleaseMode

    /**
     * Get the current loader version from the Fabric mod metadata.
     * This can be used to check if an update is available.
     */
    fun getCurrentLoaderVersion(): String? {
        return try {
            val fabricLoader = FabricLoader.getInstance()
            val loaderMod = fabricLoader.getModContainer("lambda-loader")

            if (loaderMod.isPresent) {
                val version = loaderMod.get().metadata.version.friendlyString
                if (ConfigManager.config.debug) logger.info("Current loader version: $version")
                return version
            }

            if (ConfigManager.config.debug) logger.warning("Could not determine current loader version")
            null
        } catch (e: Exception) {
            if (ConfigManager.config.debug) {
                logger.warning("Error reading loader version: ${e.message}")
                e.printStackTrace()
            }
            null
        }
    }

    /**
     * Check if an update is available by comparing the current version with the latest available.
     * Returns the latest version if an update is available, otherwise null.
     */
    fun checkForUpdate(): String? {
        return try {
            val currentVersion = getCurrentLoaderVersion()

            val latestVersion = when (getReleaseMode()) {
                ReleaseMode.Stable -> checkStableVersion()
                ReleaseMode.Snapshot -> checkSnapshotVersion()
            }

            if (latestVersion == null) {
                logger.warning("Could not fetch latest loader version")
                return null
            }

            if (currentVersion == null) {
                logger.info("Latest loader version available: $latestVersion")
                return latestVersion
            }

            if (currentVersion != latestVersion) {
                logger.info("Loader update available: $currentVersion -> $latestVersion")
                return latestVersion
            } else {
                if (ConfigManager.config.debug) logger.info("Loader is up to date: $currentVersion")
                return null
            }
        } catch (e: Exception) {
            logger.severe("Error checking for loader update: ${e.message}")
            e.printStackTrace()
            null
        }
    }
}
