package com.lambda.loader.config

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import java.io.File

object ConfigManager {

    private val configFile: File = File("lambda/config", "modules.json")
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    var config: Config = loadConfig()

    private fun loadConfig(): Config {
        return if (configFile.exists()) {
            try {
                val json = configFile.readText()
                val rootObject = gson.fromJson(json, JsonObject::class.java)

                val autoUpdater = rootObject.getAsJsonObject("AutoUpdater")

                if (autoUpdater != null) {
                    val debug = autoUpdater.get("Debug")?.asBoolean ?: false
                    val clientBranch = autoUpdater.get("Client Branch")?.asString ?: "Snapshot"
                    val loaderBranch = autoUpdater.get("Loader Branch")?.asString ?: "Stable"

                    Config(
                        clientReleaseMode = branchToReleaseMode(clientBranch),
                        loaderReleaseMode = branchToReleaseMode(loaderBranch),
                        debug = debug
                    )
                } else Config()
            } catch (_: Exception) {
                Config()
            }
        } else Config()
    }

    private fun branchToReleaseMode(branch: String): ReleaseMode {
        return when (branch.uppercase()) {
            "Stable" -> ReleaseMode.Stable
            else -> ReleaseMode.Snapshot
        }
    }
}
