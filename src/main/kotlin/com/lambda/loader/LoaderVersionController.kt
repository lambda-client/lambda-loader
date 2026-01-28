package com.lambda.loader

import com.lambda.loader.config.ConfigManager
import com.lambda.loader.config.ReleaseMode
import java.net.URI
import java.net.URL

/**
 * Version controller for Lambda-Loader self-updates.
 * Fetches loader updates from a separate Maven repository.
 */
class LoaderVersionController(
    cache: Cache = Cache(),
    /**
     * The maven repository URL for the loader artifacts.
     * Can be overridden for testing or custom repositories.
     */
    loaderMavenUrl: String = "https://maven.lambda-client.org"
) : BaseMavenVersionController(cache, versionMatchingEnabled = false) {

    override val mavenUrl: String = loaderMavenUrl
    override val releasesMetaUrl: URL =
        URI("$mavenUrl/releases/com/lambda/loader/maven-metadata.xml").toURL()
    override val snapshotMetaUrl: URL =
        URI("$mavenUrl/snapshots/com/lambda/loader/maven-metadata.xml").toURL()
    override val artifactPath: String = "com/lambda/loader"
    override val artifactName: String = "loader"

    /**
     * Loader doesn't need version matching - we always want the latest version
     */
    override fun getVersionToMatch(): String? = null

    /**
     * Use the loader-specific release mode from config
     */
    override fun getReleaseMode(): ReleaseMode = ConfigManager.config.loaderReleaseMode

    /**
     * Get the current loader version from the Fabric mod metadata.
     * This can be used to check if an update is available.
     */
    fun getCurrentLoaderVersion(): String? {
        return try {
            // Try to read from Fabric mod container
            val fabricLoader = net.fabricmc.loader.api.FabricLoader.getInstance()
            val loaderMod = fabricLoader.getModContainer("lambda-loader")

            if (loaderMod.isPresent) {
                val version = loaderMod.get().metadata.version.friendlyString
                if (ConfigManager.config.debug) {
                    logger.info("Current loader version: $version")
                }
                return version
            }

            // Fallback: try package implementation version
            val version = this::class.java.`package`?.implementationVersion
            if (version != null) {
                if (ConfigManager.config.debug) {
                    logger.info("Current loader version (from package): $version")
                }
                return version
            }

            if (ConfigManager.config.debug) {
                logger.warning("Could not determine current loader version")
            }
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
     * Check if an update is available by comparing current version with latest available.
     * Returns the latest version if an update is available, null otherwise.
     */
    fun checkForUpdate(): String? {
        return try {
            val currentVersion = getCurrentLoaderVersion()

            // Try to get latest version based on release mode, with fallback
            val latestVersion = when (getReleaseMode()) {
                ReleaseMode.STABLE -> {
                    val releaseVersion = checkReleasesVersion()
                    if (releaseVersion == null) {
                        logger.warning("No stable loader version found, falling back to snapshot")
                        checkSnapshotVersion()
                    } else {
                        releaseVersion
                    }
                }
                ReleaseMode.SNAPSHOT -> checkSnapshotVersion()
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
                if (ConfigManager.config.debug) {
                    logger.info("Loader is up to date: $currentVersion")
                }
                return null
            }
        } catch (e: Exception) {
            logger.severe("Error checking for loader update: ${e.message}")
            e.printStackTrace()
            null
        }
    }
}
