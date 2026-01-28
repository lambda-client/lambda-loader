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

                // Extract AutoUpdater object
                val autoUpdater = rootObject.getAsJsonObject("AutoUpdater")

                if (autoUpdater != null) {
                    val debug = autoUpdater.get("Debug")?.asBoolean ?: false
                    val loaderBranch = autoUpdater.get("Loader Branch")?.asString ?: "RELEASE"
                    val clientBranch = autoUpdater.get("Client Branch")?.asString ?: "RELEASE"

                    Config(
                        clientReleaseMode = branchToReleaseMode(clientBranch),
                        loaderReleaseMode = branchToReleaseMode(loaderBranch),
                        debug = debug
                    )
                } else {
                    // AutoUpdater section doesn't exist, use defaults
                    Config()
                }
            } catch (_: Exception) {
                // If parsing fails, use defaults
                Config()
            }
        } else {
            // Config file doesn't exist, use defaults
            Config()
        }
    }

    private fun branchToReleaseMode(branch: String): ReleaseMode {
        return when (branch.uppercase()) {
            "RELEASE", "STABLE" -> ReleaseMode.STABLE
            "SNAPSHOT" -> ReleaseMode.SNAPSHOT
            else -> ReleaseMode.STABLE
        }
    }

}
