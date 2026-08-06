package com.lambda.loader

import com.lambda.loader.config.ConfigManager
import com.lambda.loader.config.ReleaseMode
import java.io.File
import java.net.URI
import java.net.URL
import java.util.logging.Logger
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.system.exitProcess

/**
 * Base class for version controllers that fetch artifacts from Maven repositories.
 * Provides common functionality for checking versions, downloading artifacts, and caching.
 */
abstract class BaseMavenVersionController(
    protected val cache: Cache = Cache(),
    protected val versionMatchingEnabled: Boolean = true
) {
    protected val logger: Logger = Logger.getLogger("Lambda-Loader")

    abstract val mavenUrl: String
    abstract val stableMetaUrl: URL
    abstract val devMetaUrl: URL
    abstract val artifactPath: String
    abstract val artifactName: String

    protected open fun getReleaseMode(): ReleaseMode = ConfigManager.config.clientReleaseMode

    protected open fun getVersionToMatch(): String? = null

    protected fun checkStableVersion(): String? {
        return try {
            val xml = stableMetaUrl.readText()
            parseLatestVersionForMinecraft(xml)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    protected fun checkDevVersion(): String? {
        return try {
            val xml = devMetaUrl.readText()
            parseLatestVersionForMinecraft(xml)
        } catch (e: Exception) {
            if (ConfigManager.config.debug) e.printStackTrace()
            null
        }
    }

    protected open fun parseLatestVersionForMinecraft(xml: String): String? {
        return try {
            val factory = DocumentBuilderFactory.newInstance()
            val builder = factory.newDocumentBuilder()
            val document = builder.parse(xml.byteInputStream())

            val versionNodes = document.getElementsByTagName("version")
            val versions = mutableListOf<String>()

            (0 until versionNodes.length).forEach { i ->
                val version = versionNodes.item(i).textContent
                versions.add(version)
            }

            val versionToMatch = getVersionToMatch()

            if (ConfigManager.config.debug) {
                if (versionToMatch != null) logger.info("Target version: $versionToMatch")
                logger.info("Available Maven versions: ${versions.joinToString(", ")}")
            }

            val matchingVersions = if (versionToMatch != null && versionMatchingEnabled) {
                versions.filter { version ->
                    val mcVersionInArtifact = version.substringAfter("+").substringBefore("-")

                    val normalizedArtifactVersion = mcVersionInArtifact.replace(".", "")
                    val normalizedTargetVersion = versionToMatch.replace(".", "")

                    normalizedArtifactVersion == normalizedTargetVersion
                }
            } else versions

            if (matchingVersions.isEmpty()) {
                if (ConfigManager.config.debug) {
                    val versionMsg = versionToMatch?.let { "for version $it" } ?: ""
                    logger.warning("No $artifactName versions found $versionMsg")
                    logger.warning("Available versions: ${versions.joinToString(", ")}")
                }
                return null
            }

            val latestVersion = matchingVersions.last()
            if (ConfigManager.config.debug) {
                val versionMsg = versionToMatch?.let { "for version $it" } ?: ""
                logger.info("Found latest $artifactName version $versionMsg: $latestVersion")
            }
            latestVersion
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    protected fun getDevJarUrl(): String? {
        val version = checkDevVersion() ?: return null
        return "$mavenUrl/dev/$artifactPath/$version/$artifactName-$version.jar"
    }

    protected fun getDevChecksumUrl(): String? {
        val jarUrl = getDevJarUrl() ?: return null
        return "$jarUrl.md5"
    }

    protected fun getStableJarUrl(): String? {
        val version = checkStableVersion() ?: return null
        return "$mavenUrl/stable/$artifactPath/$version/$artifactName-$version.jar"
    }

    protected fun getStableChecksumUrl(): String? {
        val jarUrl = getStableJarUrl() ?: return null
        return "$jarUrl.md5"
    }

    protected fun getJarUrl(): String? {
        return when (getReleaseMode()) {
            ReleaseMode.Stable -> {
                val stableUrl = getStableJarUrl()
                if (stableUrl == null) {
                    val versionMsg = getVersionToMatch()?.let { "for version $it" } ?: ""
                    logger.warning("No stable version found $versionMsg, falling back to dev")
                    getDevJarUrl()
                } else stableUrl
            }
            ReleaseMode.Dev -> getDevJarUrl()
        }
    }

    protected fun getChecksumUrl(): String? {
        return when (getReleaseMode()) {
            ReleaseMode.Stable -> {
                val stableChecksumUrl = getStableChecksumUrl()
                if (stableChecksumUrl == null) {
                    val versionMsg = getVersionToMatch()?.let { "for version $it" } ?: ""
                    logger.warning("No stable version checksum found $versionMsg, falling back to dev")
                    getDevChecksumUrl()
                } else stableChecksumUrl
            }
            ReleaseMode.Dev -> getDevChecksumUrl()
        }
    }

    protected fun getCacheFileName(): String? {
        val version = when (getReleaseMode()) {
            ReleaseMode.Stable -> {
                val stableVersion = checkStableVersion()
                if (stableVersion == null) {
                    val versionMsg = getVersionToMatch()?.let { "for version $it" } ?: ""
                    logger.warning("No stable cache filename found $versionMsg, falling back to dev")
                    checkDevVersion() ?: return null
                } else stableVersion
            }
            ReleaseMode.Dev -> checkDevVersion() ?: return null
        }
        return "$artifactName-$version.jar"
    }

    protected fun downloadChecksum(): String? {
        return try {
            val checksumUrl = getChecksumUrl() ?: return null
            URI(checksumUrl).toURL().readText().trim().split(" ").first()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    protected fun isLatestVersionCached(): Boolean {
        val fileName = getCacheFileName() ?: return false
        val expectedChecksum = downloadChecksum() ?: return false
        return cache.checkVersionChecksum(fileName, expectedChecksum)
    }

    protected fun downloadJar(): ByteArray? {
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

    protected fun ensureLatestVersionCached(): Boolean {
        return try {
            if (isLatestVersionCached()) {
                if (ConfigManager.config.debug) logger.info("Latest version is already cached with valid checksum")
                return true
            }

            if (ConfigManager.config.debug) logger.info("Latest version not cached or checksum invalid, downloading...")

            val fileName = getCacheFileName() ?: return false

            val jarData = downloadJar() ?: run {
                logger.severe("Failed to download JAR")
                return false
            }

            val expectedChecksum = downloadChecksum() ?: run {
                logger.severe("Failed to download checksum")
                return false
            }

            val actualChecksum = cache.checkSumBytes(jarData)
            if (actualChecksum != expectedChecksum) {
                logger.severe("Checksum mismatch! Expected: $expectedChecksum, Got: $actualChecksum")
                return false
            }

            cache.cacheVersion(fileName, jarData)
            if (ConfigManager.config.debug) logger.info("Successfully cached version: $fileName")

            val verified = cache.checkVersionChecksum(fileName, expectedChecksum)
            if (ConfigManager.config.debug)
                if (verified) logger.fine("Cache verification successful")
                else logger.warning("Cache verification failed")
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
            if (!ensureLatestVersionCached()) {
                val stableVersion = checkStableVersion()
                val devVersion = checkDevVersion()

                if (stableVersion == null && devVersion == null) {
                    val versionMsg = getVersionToMatch()?.let { "for version $it" } ?: ""
                    logger.severe("============================================================")
                    logger.severe("FATAL ERROR: No $artifactName version found!")
                    if (versionMsg.isNotEmpty()) {
                        logger.severe("Target version: $versionMsg")
                    }
                    logger.severe("Neither stable nor dev versions are available.")
                    logger.severe("Please check:")
                    logger.severe("  1. Your internet connection")
                    logger.severe("  2. Maven repository availability at: $mavenUrl")
                    logger.severe("  3. If $artifactName supports the required version")
                    logger.severe("============================================================")
                } else {
                    logger.severe("Failed to ensure latest version is cached")
                }
                return null
            }

            val fileName = getCacheFileName()
            if (fileName == null) {
                logger.severe("Failed to get cache filename after successful caching")
                return null
            }

            val jarFile = cache.getCachedVersion(fileName)
            if (jarFile == null) {
                logger.severe("JAR file does not exist after caching: $fileName")
                exitProcess(1)
            }

            if (ConfigManager.config.debug) logger.info("Latest version ready: ${jarFile.absolutePath}")
            jarFile
        } catch (e: Exception) {
            logger.severe("Failed to get or download latest version: ${e.message}")
            e.printStackTrace()
            null
        }
    }
}
