package com.lambda.lambdaLoader.util

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.lambda.lambdaLoader.config.ConfigManager
import net.fabricmc.loader.impl.FabricLoaderImpl
import net.fabricmc.loader.impl.launch.FabricLauncherBase
import net.fabricmc.loader.impl.lib.classtweaker.impl.ClassTweakerImpl
import net.fabricmc.loader.impl.lib.classtweaker.reader.ClassTweakerReaderImpl
import net.fabricmc.loader.impl.metadata.DependencyOverrides
import net.fabricmc.loader.impl.metadata.LoaderModMetadata
import net.fabricmc.loader.impl.metadata.VersionOverrides
import java.io.File
import java.io.InputStreamReader
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.logging.Logger
import java.util.zip.ZipFile

object FabricUtil {

    private val logger: Logger = Logger.getLogger("Lambda-Loader")

    fun injectAccessWidener(resourcePath: String, namespace: String = "intermediary") {
        try {
            // Get the existing ClassTweaker from FabricLoader
            val classTweaker = FabricLoaderImpl.INSTANCE.classTweaker as ClassTweakerImpl

            // Create a reader with the ClassTweaker as the visitor
            val reader = ClassTweakerReaderImpl(classTweaker)

            // Read from resource
            val content = this::class.java.classLoader.getResourceAsStream(resourcePath)
                ?.readBytes()
                ?: throw IllegalArgumentException("Access widener file not found: $resourcePath")

            // Read into the ClassTweaker (which creates AccessWidenerImpl instances internally)
            reader.read(content, namespace)

            if (ConfigManager.config.debug) {
                logger.info("Successfully loaded access widener from $resourcePath")
            }
        } catch (e: Exception) {
            logger.severe("Failed to inject access widener: ${e.message}")
            e.printStackTrace()
        }
    }

    fun addToClassPath(jarFile: File) {
        FabricLauncherBase.getLauncher().addToClassPath(jarFile.toPath())
    }

    fun addToClassPath(jarPath: Path) {
        FabricLauncherBase.getLauncher().addToClassPath(jarPath)
    }

    /**
     * Loads nested JARs from META-INF/jars/ directory and adds them to classpath
     * Fabric Loader stores bundled dependencies in this directory for jar-in-jar functionality
     * Extracts nested JARs to temp directory for classpath access
     */
    fun loadNestedJars(jarFile: File): List<Path> {
        val nestedJarPaths = mutableListOf<Path>()

        try {
            // Create temp directory for nested JARs
            val tempDir = Files.createTempDirectory("lambda-loader-nested")
            if (ConfigManager.config.debug) {
                logger.info("Created temp directory for nested JARs: $tempDir")
            }

            // Create URI for the JAR file system
            val jarUri = java.net.URI.create("jar:${jarFile.toURI()}")

            // Open the JAR as a file system
            FileSystems.newFileSystem(jarUri, mapOf<String, Any>()).use { fs ->
                val jarsDir = fs.getPath("META-INF/jars")

                if (Files.exists(jarsDir)) {
                    Files.walk(jarsDir).use { stream ->
                        stream.filter { path ->
                            Files.isRegularFile(path) && path.toString().endsWith(".jar")
                        }.forEach { nestedJarPath ->
                            if (ConfigManager.config.debug) {
                                logger.info("Found nested JAR: $nestedJarPath")
                            }

                            // Extract to temp directory
                            val targetPath = tempDir.resolve(nestedJarPath.fileName.toString())
                            Files.copy(
                                nestedJarPath,
                                targetPath,
                                StandardCopyOption.REPLACE_EXISTING
                            )

                            // Add extracted JAR to classpath
                            addToClassPath(targetPath)
                            nestedJarPaths.add(targetPath)

                            if (ConfigManager.config.debug) {
                                logger.info("Extracted and added nested JAR to classpath: ${nestedJarPath.fileName}")
                            }
                        }
                    }
                }
            }

            if (ConfigManager.config.debug) {
                logger.info("Successfully loaded ${nestedJarPaths.size} nested JARs from ${jarFile.name}")
            }
        } catch (e: Exception) {
            logger.severe("Failed to load nested JARs: ${e.message}")
            e.printStackTrace()
        }

        return nestedJarPaths
    }

    /**
     * Loads mod metadata from a JAR file by parsing its fabric.mod.json
     */
    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    fun loadMetadataFromJar(jarFile: File): LoaderModMetadata? {
        return try {
            ZipFile(jarFile).use { zip ->
                val entry = zip.getEntry("fabric.mod.json")
                    ?: throw IllegalArgumentException("No fabric.mod.json found in JAR")

                // Use ModMetadataParser to parse the metadata
                val parserClass = Class.forName("net.fabricmc.loader.impl.metadata.ModMetadataParser")
                val parseMetadataMethod = parserClass.getDeclaredMethod(
                    "parseMetadata",
                    java.io.InputStream::class.java,
                    String::class.java,
                    java.util.List::class.java,
                    Class.forName("net.fabricmc.loader.impl.metadata.VersionOverrides"),
                    Class.forName("net.fabricmc.loader.impl.metadata.DependencyOverrides"),
                    Boolean::class.javaPrimitiveType
                )
                parseMetadataMethod.isAccessible = true

                val versionOverrides = VersionOverrides()

                // DependencyOverrides takes a Path parameter - use a temporary directory
                val tempPath = Files.createTempDirectory("lambda-loader-deps")
                val depOverrides = DependencyOverrides(tempPath)

                val metadata = parseMetadataMethod.invoke(
                    null, // static method
                    zip.getInputStream(entry),
                    jarFile.name,
                    emptyList<String>(), // parent mod ids
                    versionOverrides,
                    depOverrides,
                    false // isDevelopment
                ) as LoaderModMetadata

                if (ConfigManager.config.debug) {
                    logger.info("Successfully loaded metadata from JAR: ${metadata.id}")
                }
                metadata
            }
        } catch (e: Exception) {
            logger.severe("Failed to load metadata from JAR: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    /**
     * Creates a mod candidate with proper JAR paths, simulating how Fabric Loader does it
     */
    private fun createModCandidate(metadata: LoaderModMetadata, jarFile: File): Any? {
        return try {
            val modCandidateClass = Class.forName("net.fabricmc.loader.impl.discovery.ModCandidateImpl")

            // Use the createPlain static factory method
            val createPlainMethod = modCandidateClass.getDeclaredMethod(
                "createPlain",
                List::class.java,
                LoaderModMetadata::class.java,
                Boolean::class.javaPrimitiveType,
                Collection::class.java
            )
            createPlainMethod.isAccessible = true

            // Create a mod candidate with the JAR file path - just like Fabric Loader does
            val candidate = createPlainMethod.invoke(
                null, // static method
                listOf(jarFile.toPath()), // provide the JAR file path
                metadata,
                false, // requiresRemap - already in production format
                emptyList<Any>() // nested mods
            )

            if (ConfigManager.config.debug) {
                logger.info("Successfully created mod candidate for ${metadata.id} with JAR path: ${jarFile.absolutePath}")
            }
            candidate
        } catch (e: Exception) {
            logger.severe("Failed to create mod candidate: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    /**
     * Converts a mod candidate to a mod container
     */
    private fun candidateToContainer(candidate: Any): Any? {
        return try {
            val modContainerClass = Class.forName("net.fabricmc.loader.impl.ModContainerImpl")
            val modCandidateClass = Class.forName("net.fabricmc.loader.impl.discovery.ModCandidateImpl")

            // ModContainerImpl(ModCandidateImpl candidate)
            val constructor = modContainerClass.getDeclaredConstructor(modCandidateClass)
            constructor.isAccessible = true

            val container = constructor.newInstance(candidate)
            if (ConfigManager.config.debug) {
                logger.info("Successfully converted candidate to mod container")
            }
            container
        } catch (e: Exception) {
            logger.severe("Failed to convert candidate to container: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    /**
     * Adds a mod container to the Fabric Loader's mod list
     */
    private fun addModContainer(container: Any): Boolean {
        return try {
            val loader = FabricLoaderImpl.INSTANCE

            // Get the mods field
            val loaderClass = loader::class.java
            val modsField = loaderClass.getDeclaredField("mods")
            modsField.isAccessible = true

            @Suppress("UNCHECKED_CAST")
            val modsList = modsField.get(loader) as MutableList<Any>
            modsList.add(container)

            // Also need to add to modMap (Map<String, ModContainerImpl>)also
            val modMapField = loaderClass.getDeclaredField("modMap")
            modMapField.isAccessible = true

            @Suppress("UNCHECKED_CAST")
            val modMap = modMapField.get(loader) as MutableMap<String, Any>
            // Get the mod ID from the container
            val getMetadataMethod = container::class.java.getDeclaredMethod("getMetadata")
            getMetadataMethod.isAccessible = true
            val metadata = getMetadataMethod.invoke(container) as LoaderModMetadata
            val modId = metadata.id

            modMap[modId] = container

            if (ConfigManager.config.debug) {
                logger.info("Successfully added mod container for $modId to Fabric Loader")
            }
            true
        } catch (e: Exception) {
            logger.severe("Failed to add mod container to Fabric Loader: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    /**
     * Reads the fabric.mod.json from a JAR file and returns it as a JsonObject
     */
    fun readFabricModJson(jarFile: File): JsonObject? {
        return try {
            ZipFile(jarFile).use { zip ->
                val entry = zip.getEntry("fabric.mod.json")
                    ?: throw IllegalArgumentException("No fabric.mod.json found in JAR: ${jarFile.name}")

                val inputStream = zip.getInputStream(entry)
                val reader = InputStreamReader(inputStream, Charsets.UTF_8)
                val jsonObject = JsonParser.parseReader(reader).asJsonObject

                if (ConfigManager.config.debug) {
                    logger.info("Successfully read fabric.mod.json from ${jarFile.name}")
                }
                jsonObject
            }
        } catch (e: Exception) {
            logger.severe("Failed to read fabric.mod.json from ${jarFile.name}: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    /**
     * Gets the access widener file name from fabric.mod.json
     * Returns null if no access widener is specified
     */
    fun getAccessWidenerFileName(jarFile: File): String? {
        return try {
            val json = readFabricModJson(jarFile) ?: return null

            val accessWidener = json.get("accessWidener")?.asString

            if (ConfigManager.config.debug && accessWidener != null) {
                logger.info("Found access widener: $accessWidener in ${jarFile.name}")
            }

            accessWidener
        } catch (e: Exception) {
            logger.severe("Failed to get access widener from ${jarFile.name}: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    /**
     * Gets the list of mixin configuration files from fabric.mod.json
     * Returns an empty list if no mixins are specified
     */
    fun getMixinFileNames(jarFile: File): List<String> {
        return try {
            val json = readFabricModJson(jarFile) ?: return emptyList()

            val mixins = mutableListOf<String>()

            // Check for "mixins" field (array of strings)
            json.get("mixins")?.asJsonArray?.forEach { element ->
                if (element.isJsonPrimitive) {
                    mixins.add(element.asString)
                }
            }

            if (ConfigManager.config.debug && mixins.isNotEmpty()) {
                logger.info("Found ${mixins.size} mixin config(s) in ${jarFile.name}: ${mixins.joinToString(", ")}")
            }

            mixins
        } catch (e: Exception) {
            logger.severe("Failed to get mixins from ${jarFile.name}: ${e.message}")
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Convenience method to load metadata from JAR, create a mod candidate,
     * convert to container, and add to Fabric Loader
     */
    fun registerModFromJar(jarFile: File): Boolean {
        val metadata = loadMetadataFromJar(jarFile) ?: return false
        val candidate = createModCandidate(metadata, jarFile) ?: return false
        val container = candidateToContainer(candidate) ?: return false
        return addModContainer(container)
    }

}
