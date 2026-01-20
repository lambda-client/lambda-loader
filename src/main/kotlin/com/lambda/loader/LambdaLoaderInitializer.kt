package com.lambda.loader

import com.lambda.loader.config.ConfigManager
import com.lambda.loader.util.FabricUtil
import com.lambda.loader.util.SimpleLogFormatter
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint
import org.spongepowered.asm.mixin.Mixins
import java.util.logging.ConsoleHandler
import java.util.logging.Logger
import kotlin.system.exitProcess

class LambdaLoaderInitializer : PreLaunchEntrypoint {
    val logger: Logger = Logger.getLogger("Lambda-Loader").also {
        // Configure logger to use simple format
        it.useParentHandlers = false
        val handler = ConsoleHandler()
        handler.formatter = SimpleLogFormatter()
        it.addHandler(handler)
    }

    override fun onPreLaunch() {
        // Check for loader self-updates first
        checkForLoaderUpdate()

        // Then load Lambda Client
        loadClient()
    }

    private fun checkForLoaderUpdate() {
        try {
            logger.info("Checking for Lambda-Loader updates...")
            val updater = LoaderUpdater()
            val updateInfo = updater.checkForUpdate()

            if (updateInfo.available) {
                logger.info("═══════════════════════════════════════════════════════════")
                logger.info("Lambda-Loader update available!")
                logger.info("Current version: ${updateInfo.currentVersion ?: "unknown"}")
                logger.info("Latest version: ${updateInfo.latestVersion}")
                logger.info("═══════════════════════════════════════════════════════════")

                // Apply the update
                val applied = updater.applyUpdate(updateInfo)

                if (applied) {
                    logger.info("═══════════════════════════════════════════════════════════")
                    logger.info("Lambda-Loader has been updated successfully!")
                    logger.info("Please restart Minecraft to use the new version.")
                    logger.info("═══════════════════════════════════════════════════════════")

                    // Exit to force restart with new loader
                    // User will need to restart Minecraft manually
                    exitProcess(0)
                } else {
                    logger.warning("Failed to apply loader update, continuing with current version")
                }
            } else {
                if (ConfigManager.config.debug) {
                    logger.info("Lambda-Loader is up to date (${updateInfo.currentVersion ?: "unknown"})")
                }
            }
        } catch (e: Exception) {
            logger.warning("Error checking for loader updates: ${e.message}")
            if (ConfigManager.config.debug) {
                e.printStackTrace()
            }
            logger.info("Continuing with current loader version")
        }
    }

    private fun loadClient() {
        logger.info("Loading Lambda Client...")
        val latestVersion = VersionController().getOrDownloadLatestVersion()
        if (latestVersion == null) {
            logger.severe("Failed to download latest lambda stopping!")
            exitProcess(0)
        }

        FabricUtil.addToClassPath(latestVersion)
        FabricUtil.loadNestedJars(latestVersion)
        FabricUtil.registerModFromJar(latestVersion)

        val accessWidenerPath = FabricUtil.getAccessWidenerFileName(latestVersion)
        if (accessWidenerPath != null) {
            FabricUtil.injectAccessWidener(accessWidenerPath)
        }

        for (mixinFile in FabricUtil.getMixinFileNames(latestVersion)) {
            Mixins.addConfiguration(mixinFile)
        }

        logger.info("Lambda Client loaded successfully")
    }
}