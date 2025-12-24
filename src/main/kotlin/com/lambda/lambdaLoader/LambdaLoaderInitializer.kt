package com.lambda.lambdaLoader

import com.lambda.lambdaLoader.util.FabricUtil
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint
import org.spongepowered.asm.mixin.Mixins
import java.util.logging.Logger
import kotlin.system.exitProcess

class LambdaLoaderInitializer : PreLaunchEntrypoint {
    val logger: Logger = Logger.getLogger("Lambda-Loader")
    override fun onPreLaunch() {
        val latestVersion = VersionController().getOrDownloadLatestVersion()
        if (latestVersion == null) {
            logger.severe("Failed to download latest lambda stopping!")
            exitProcess(0)
        }
        FabricUtil.addToClassPath(latestVersion)
        FabricUtil.loadNestedJars(latestVersion)
        FabricUtil.registerModFromJar(latestVersion)
        val accessWidenerPath = FabricUtil.getAccessWidenerFileName(latestVersion);
        if(accessWidenerPath != null) FabricUtil.injectAccessWidener(accessWidenerPath)
        for (mixinFile in FabricUtil.getMixinFileNames(latestVersion)) {
            Mixins.addConfiguration(mixinFile)
        }

    }


}