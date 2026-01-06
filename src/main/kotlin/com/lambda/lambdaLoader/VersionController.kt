package com.lambda.lambdaLoader

import com.lambda.lambdaLoader.config.ConfigManager
import com.lambda.lambdaLoader.config.ReleaseMode
import net.fabricmc.loader.impl.FabricLoaderImpl
import java.io.File
import java.net.URI
import java.net.URL
import java.util.logging.Logger
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.system.exitProcess

class VersionController(
    private val cache: Cache = Cache(),
    private val minecraftVersionOverride: String? = null
) {

    val mavenUrl: String = "https://maven.lambda-client.org"
    val releasesMetaUrl: URL =
        URI("https://maven.lambda-client.org/releases/com/lambda/lambda/maven-metadata.xml").toURL()
    val snapshotMetaUrl: URL =
        URI("https://maven.lambda-client.org/snapshots/com/lambda/lambda/maven-metadata.xml").toURL()

    private val logger: Logger = Logger.getLogger("Lambda-Loader")

    // Get the current Minecraft version from Fabric Loader or use override for testing
    private val minecraftVersion: String by lazy {
        minecraftVersionOverride ?: FabricLoaderImpl.INSTANCE.getGameProvider().rawGameVersion
    }

    private fun checkReleasesVersion(): String? {
        return try {
            val xml = releasesMetaUrl.readText()
            parseLatestVersionForMinecraft(xml)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun checkSnapshotVersion(): String? {
        return try {
            val xml = snapshotMetaUrl.readText()
            parseLatestVersionForMinecraft(xml)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun parseLatestVersionForMinecraft(xml: String): String? {
        return try {
            val factory = DocumentBuilderFactory.newInstance()
            val builder = factory.newDocumentBuilder()
            val document = builder.parse(xml.byteInputStream())

            // Get all versions
            val versionNodes = document.getElementsByTagName("version")
            val versions = mutableListOf<String>()

            for (i in 0 until versionNodes.length) {
                val version = versionNodes.item(i).textContent
                versions.add(version)
            }

            if (ConfigManager.config.debug) {
                logger.info("Current Minecraft version: $minecraftVersion")
                logger.info("Available Maven versions: ${versions.joinToString(", ")}")
            }

            // Filter versions for current Minecraft version
            // Version format: 0.0.2+1.21.11-SNAPSHOT or 0.0.2+1.21.11
            // Handle both 1.21.1.1 and 1.21.11 formats
            val matchingVersions = versions.filter { version ->
                // Extract MC version from artifact version (after +, before - or end)
                val mcVersionInArtifact = version.substringAfter("+").substringBefore("-")

                // Normalize both versions for comparison (remove extra dots)
                val normalizedArtifactVersion = mcVersionInArtifact.replace(".", "")
                val normalizedMinecraftVersion = minecraftVersion.replace(".", "")

                normalizedArtifactVersion == normalizedMinecraftVersion
            }

            if (matchingVersions.isEmpty()) {
                if (ConfigManager.config.debug) {
                    logger.warning("No Lambda versions found for Minecraft $minecraftVersion")
                    logger.warning("Available versions: ${versions.joinToString(", ")}")
                }
                return null
            }

            // Get the latest matching version (last in the list)
            val latestVersion = matchingVersions.last()
            if (ConfigManager.config.debug) {
                logger.info("Found latest Lambda version for Minecraft $minecraftVersion: $latestVersion")
            }
            latestVersion
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    data class SnapshotInfo(
        val version: String,
        val timestamp: String,
        val buildNumber: String
    )

    private fun getLatestSnapshotInfo(): SnapshotInfo? {
        return try {
            val version = checkSnapshotVersion() ?: return null
            val snapshotMetaUrl = URI("$mavenUrl/snapshots/com/lambda/lambda/$version/maven-metadata.xml").toURL()
            val xml = snapshotMetaUrl.readText()
            parseSnapshotInfo(xml, version)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun parseSnapshotInfo(xml: String, version: String): SnapshotInfo? {
        return try {
            val factory = DocumentBuilderFactory.newInstance()
            val builder = factory.newDocumentBuilder()
            val document = builder.parse(xml.byteInputStream())

            val timestampNodes = document.getElementsByTagName("timestamp")
            val buildNumberNodes = document.getElementsByTagName("buildNumber")

            if (timestampNodes.length > 0 && buildNumberNodes.length > 0) {
                val timestamp = timestampNodes.item(0).textContent
                val buildNumber = buildNumberNodes.item(0).textContent
                SnapshotInfo(version, timestamp, buildNumber)
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun getSnapshotJarUrl(): String? {
        val snapshotInfo = getLatestSnapshotInfo() ?: return null
        val baseVersion = snapshotInfo.version.replace("-SNAPSHOT", "")
        val timestamp = snapshotInfo.timestamp
        val buildNumber = snapshotInfo.buildNumber
        return "$mavenUrl/snapshots/com/lambda/lambda/${snapshotInfo.version}/lambda-$baseVersion-$timestamp-$buildNumber.jar"
    }

    private fun getSnapshotChecksumUrl(): String? {
        val jarUrl = getSnapshotJarUrl() ?: return null
        return "$jarUrl.md5"
    }

    private fun getReleaseJarUrl(): String? {
        val version = checkReleasesVersion() ?: return null
        return "$mavenUrl/releases/com/lambda/lambda/$version/lambda-$version.jar"
    }

    private fun getReleaseChecksumUrl(): String? {
        val jarUrl = getReleaseJarUrl() ?: return null
        return "$jarUrl.md5"
    }

    private fun getJarUrl(): String? {
        return when (ConfigManager.config.releaseMode) {
            ReleaseMode.STABLE -> {
                val releaseUrl = getReleaseJarUrl()
                if (releaseUrl == null) {
                    logger.warning("No stable version found for Minecraft $minecraftVersion, falling back to snapshot")
                    getSnapshotJarUrl()
                } else {
                    releaseUrl
                }
            }
            ReleaseMode.SNAPSHOT -> getSnapshotJarUrl()
        }
    }

    private fun getChecksumUrl(): String? {
        return when (ConfigManager.config.releaseMode) {
            ReleaseMode.STABLE -> {
                val releaseChecksumUrl = getReleaseChecksumUrl()
                if (releaseChecksumUrl == null) {
                    logger.warning("No stable version checksum found for Minecraft $minecraftVersion, falling back to snapshot")
                    getSnapshotChecksumUrl()
                } else {
                    releaseChecksumUrl
                }
            }
            ReleaseMode.SNAPSHOT -> getSnapshotChecksumUrl()
        }
    }

    private fun getCacheFileName(): String? {
        return when (ConfigManager.config.releaseMode) {
            ReleaseMode.STABLE -> {
                val version = checkReleasesVersion()
                if (version == null) {
                    logger.warning("No stable cache filename found for Minecraft $minecraftVersion, falling back to snapshot")
                    val snapshotInfo = getLatestSnapshotInfo() ?: return null
                    val baseVersion = snapshotInfo.version.replace("-SNAPSHOT", "")
                    "lambda-$baseVersion-${snapshotInfo.timestamp}-${snapshotInfo.buildNumber}.jar"
                } else {
                    "lambda-$version.jar"
                }
            }

            ReleaseMode.SNAPSHOT -> {
                val snapshotInfo = getLatestSnapshotInfo() ?: return null
                val baseVersion = snapshotInfo.version.replace("-SNAPSHOT", "")
                "lambda-$baseVersion-${snapshotInfo.timestamp}-${snapshotInfo.buildNumber}.jar"
            }
        }
    }

    private fun downloadChecksum(): String? {
        return try {
            val checksumUrl = getChecksumUrl() ?: return null
            URI(checksumUrl).toURL().readText().trim().split(" ").first()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun isLatestVersionCached(): Boolean {
        val fileName = getCacheFileName() ?: return false
        val expectedChecksum = downloadChecksum() ?: return false
        return cache.checkVersionChecksum(fileName, expectedChecksum)
    }

    private fun downloadJar(): ByteArray? {
        return try {
            val jarUrl = getJarUrl() ?: return null
            if (ConfigManager.config.debug) {
                logger.info("Downloading JAR from: $jarUrl")
            }
            URI(jarUrl).toURL().readBytes()
        } catch (e: Exception) {
            logger.severe("Failed to download JAR: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    private fun ensureLatestVersionCached(): Boolean {
        return try {
            // Check if already cached with valid checksum
            if (isLatestVersionCached()) {
                if (ConfigManager.config.debug) {
                    logger.info("Latest version is already cached with valid checksum")
                }
                return true
            }

            if (ConfigManager.config.debug) {
                logger.info("Latest version not cached or checksum invalid, downloading...")
            }

            // Get the file name for caching
            val fileName = getCacheFileName() ?: return false

            // Download the JAR
            val jarData = downloadJar() ?: run {
                logger.severe("Failed to download JAR")
                return false
            }

            // Download the expected checksum
            val expectedChecksum = downloadChecksum() ?: run {
                logger.severe("Failed to download checksum")
                return false
            }

            // Verify downloaded data matches checksum
            val actualChecksum = cache.checksumBytes(jarData)
            if (actualChecksum != expectedChecksum) {
                logger.severe("Checksum mismatch! Expected: $expectedChecksum, Got: $actualChecksum")
                return false
            }

            // Cache the verified JAR
            cache.cacheVersion(fileName, jarData)
            if (ConfigManager.config.debug) {
                logger.info("Successfully cached version: $fileName")
            }

            // Verify it was cached correctly
            val verified = cache.checkVersionChecksum(fileName, expectedChecksum)
            if (ConfigManager.config.debug) {
                if (verified) {
                    logger.fine("Cache verification successful")
                } else {
                    logger.warning("Cache verification failed")
                }
            }
            verified
        } catch (e: Exception) {
            logger.severe("Error ensuring latest version cached: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    /**
     * Ensures the latest version for the current release mode is cached and returns the JAR file.
     * Automatically checks if cached, validates checksum, and downloads if needed.
     *
     * @return The cached JAR file, or null if download/caching failed
     */
    fun getOrDownloadLatestVersion(): File? {
        return try {
            // Ensure the latest version is cached
            if (!ensureLatestVersionCached()) {
                // Check if both stable and snapshot versions are unavailable
                val stableVersion = checkReleasesVersion()
                val snapshotVersion = checkSnapshotVersion()

                if (stableVersion == null && snapshotVersion == null) {
                    logger.severe("═══════════════════════════════════════════════════════════")
                    logger.severe("FATAL ERROR: No Lambda Client version found!")
                    logger.severe("Minecraft version: $minecraftVersion")
                    logger.severe("Neither STABLE nor SNAPSHOT versions are available for this Minecraft version.")
                    logger.severe("Please check:")
                    logger.severe("  1. Your internet connection")
                    logger.severe("  2. Maven repository availability at: $mavenUrl")
                    logger.severe("  3. If Lambda Client supports Minecraft $minecraftVersion")
                    logger.severe("═══════════════════════════════════════════════════════════")
                } else {
                    logger.severe("Failed to ensure latest version is cached")
                }
                return null
            }

            // Get the cached filename
            val fileName = getCacheFileName()
            if (fileName == null) {
                logger.severe("Failed to get cache filename after successful caching")
                return null
            }

            // Get the cached version file
            val jarFile = cache.getCachedVersion(fileName)
            if (jarFile == null) {
                logger.severe("JAR file does not exist after caching: $fileName")
                exitProcess(1)
            }

            if (ConfigManager.config.debug) {
                logger.info("Latest version ready: ${jarFile.absolutePath}")
            }
            jarFile
        } catch (e: Exception) {
            logger.severe("Failed to get or download latest version: ${e.message}")
            e.printStackTrace()
            null
        }
    }
}