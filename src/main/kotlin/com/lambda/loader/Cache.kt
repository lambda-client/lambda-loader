package com.lambda.loader

import com.lambda.loader.config.ConfigManager
import java.io.File
import java.security.MessageDigest
import java.util.logging.Logger

class Cache(baseDir: File = File("lambda")) {
    val cacheDir: File = File(baseDir, "cache")
    val versionCacheDir: File = File(cacheDir, "version")
    val md: MessageDigest = MessageDigest.getInstance("MD5")
    private val logger: Logger = Logger.getLogger("Lambda-Loader")

    init {
        if (!cacheDir.exists()) cacheDir.mkdirs()
        if (!versionCacheDir.exists()) versionCacheDir.mkdirs()
    }


    private fun checkSumBytes(bytes: ByteArray): String {
        val digest = md.digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun clearOldVersions(currentFileName: String) {
        val baseName = currentFileName.substringBeforeLast("-").substringBeforeLast(".")

        versionCacheDir.listFiles()?.forEach { file ->
            if (file.isFile && file.name.endsWith(".jar") && file.name != currentFileName) {
                val fileBaseName = file.name.substringBeforeLast("-").substringBeforeLast(".")
                if (fileBaseName == baseName) {
                    val deleted = file.delete()
                    if (ConfigManager.config.debug)
                        if (deleted) logger.info("Deleted old version: ${file.name}")
                        else logger.warning("Failed to delete old version: ${file.name}")
                }
            }
        }
    }

    fun cacheVersion(name: String, data: ByteArray) {
        clearOldVersions(name)

        val cachedFile = File(versionCacheDir, name)
        cachedFile.writeBytes(data)

        if (ConfigManager.config.debug) logger.info("Cached new version: $name")
    }


    private fun getCachedVersionBytes(name: String): ByteArray? {
        val cachedFile = File(versionCacheDir, name)
        return if (cachedFile.exists()) cachedFile.readBytes() else null
    }

    fun getCachedVersion(name: String): File? {
        val cachedFile = File(versionCacheDir, name)
        return if (cachedFile.exists()) cachedFile else null
    }

    fun checkVersionChecksum(name: String, checksum: String): Boolean {
        val cachedData = getCachedVersionBytes(name) ?: return false
        return checkSumBytes(cachedData) == checksum.lowercase()
    }

    fun checksumBytes(bytes: ByteArray): String {
        return checkSumBytes(bytes)
    }

}