package com.lambda.loader

import com.lambda.loader.config.ConfigManager
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.logging.Logger
import kotlin.system.exitProcess

/**
 * Handles automatic updates for the Lambda-Loader itself.
 * Checks for updates on startup and replaces the loader JAR if a newer version is available.
 */
class LoaderUpdater(
    private val loaderController: LoaderVersionController = LoaderVersionController()
) {
    private val logger: Logger = Logger.getLogger("Lambda-Loader")

    /**
     * Get the file path of the currently running loader JAR.
     * Returns null if the JAR path cannot be determined.
     */
    private fun getCurrentLoaderJarPath(): File? {
        return try {
            val codeSource = this::class.java.protectionDomain.codeSource
            if (codeSource != null) {
                val location = codeSource.location
                val path = location.toURI().path
                val file = File(path)

                if (file.exists() && file.name.endsWith(".jar")) {
                    if (ConfigManager.config.debug) logger.info("Current loader JAR: ${file.absolutePath}")
                    return file
                }
            }

            logger.warning("Could not determine loader JAR path from code source")
            null
        } catch (e: Exception) {
            logger.warning("Error getting current loader JAR path: ${e.message}")
            null
        }
    }

    data class UpdateInfo(
        val available: Boolean,
        val currentVersion: String?,
        val latestVersion: String?,
        val updateFile: File?
    )

    /**
     * Check if a loader update is available.
     * Downloads the update if found but does not apply it yet.
     */
    fun checkForUpdate(): UpdateInfo {
        try {
            val currentVersion = loaderController.getCurrentLoaderVersion()
            val latestVersion = loaderController.checkForUpdate()

            if (latestVersion != null) {
                if (ConfigManager.config.debug) logger.info("Update available: $currentVersion -> $latestVersion")

                val updateFile = loaderController.getOrDownloadLatestVersion()

                return UpdateInfo(
                    available = true,
                    currentVersion = currentVersion,
                    latestVersion = latestVersion,
                    updateFile = updateFile
                )
            } else {
                if (ConfigManager.config.debug) logger.info("Loader is up to date: $currentVersion")
                return UpdateInfo(
                    available = false,
                    currentVersion = currentVersion,
                    latestVersion = currentVersion,
                    updateFile = null
                )
            }
        } catch (e: Exception) {
            logger.severe("Error checking for loader update: ${e.message}")
            e.printStackTrace()
            return UpdateInfo(
                available = false,
                currentVersion = null,
                latestVersion = null,
                updateFile = null
            )
        }
    }

    /**
     * Applies a loader update by replacing the current JAR with the new one.
     * Returns true if the update was applied successfully.
     */
    fun applyUpdate(updateInfo: UpdateInfo): Boolean {
        if (!updateInfo.available || updateInfo.updateFile == null) {
            logger.warning("No update available to apply")
            return false
        }

        val currentJar = getCurrentLoaderJarPath()
        if (currentJar == null) {
            logger.severe("Cannot apply update: Unable to locate current loader JAR")
            return false
        }

        return try {
            logger.info("═══════════════════════════════════════════════════════════")
            logger.info("Applying loader update: ${updateInfo.currentVersion} -> ${updateInfo.latestVersion}")
            logger.info("Current JAR: ${currentJar.absolutePath}")
            logger.info("Update JAR: ${updateInfo.updateFile.absolutePath}")

            val backupFile = File(currentJar.parentFile, "${currentJar.name}.backup")
            if (ConfigManager.config.debug) logger.info("Creating backup: ${backupFile.absolutePath}")

            Files.copy(
                currentJar.toPath(),
                backupFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING
            )

            if (ConfigManager.config.debug) logger.info("Replacing loader JAR...")

            Files.copy(
                updateInfo.updateFile.toPath(),
                currentJar.toPath(),
                StandardCopyOption.REPLACE_EXISTING
            )

            backupFile.delete()

            logger.info("Loader update applied successfully!")
            logger.info("═══════════════════════════════════════════════════════════")

            true
        } catch (e: IOException) {
            logger.severe("Failed to apply loader update: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    /**
     * Restore from backup if it exists.
     * Useful if an update caused issues.
     */
    fun restoreFromBackup(): Boolean {
        val currentJar = getCurrentLoaderJarPath() ?: return false
        val backupFile = File(currentJar.parentFile, "${currentJar.name}.backup")

        if (!backupFile.exists()) {
            logger.warning("No backup file found at: ${backupFile.absolutePath}")
            return false
        }

        return try {
            logger.info("Restoring loader from backup: ${backupFile.absolutePath}")

            Files.copy(
                backupFile.toPath(),
                currentJar.toPath(),
                StandardCopyOption.REPLACE_EXISTING
            )

            backupFile.delete()

            logger.info("Loader restored from backup successfully!")
            true
        } catch (e: IOException) {
            logger.severe("Failed to restore from backup: ${e.message}")
            e.printStackTrace()
            false
        }
    }
}
