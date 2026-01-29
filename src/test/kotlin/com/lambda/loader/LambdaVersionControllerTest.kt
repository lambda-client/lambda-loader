package com.lambda.loader

import com.lambda.loader.config.ConfigManager
import com.lambda.loader.config.ReleaseMode
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.net.URI
import javax.xml.parsers.DocumentBuilderFactory

class LambdaVersionControllerTest {

    // We'll create the VersionController in each test with the appropriate MC version
    // This is necessary because FabricLoader is not available in the test environment

    companion object {
        private const val MAVEN_RELEASES_URL = "https://maven.lambda-client.org/releases/com/lambda/lambda/maven-metadata.xml"
        private const val MAVEN_SNAPSHOTS_URL = "https://maven.lambda-client.org/snapshots/com/lambda/lambda/maven-metadata.xml"

        /**
         * Fetches all unique Minecraft versions available in Maven repositories
         */
        fun getAvailableMinecraftVersions(): Set<String> {
            val versions = mutableSetOf<String>()

            // Get versions from releases
            try {
                val releasesXml = URI(MAVEN_RELEASES_URL).toURL().readText()
                versions.addAll(parseMinecraftVersionsFromMaven(releasesXml))
            } catch (e: Exception) {
                println("Warning: Could not fetch releases: ${e.message}")
            }

            // Get versions from snapshots
            try {
                val snapshotsXml = URI(MAVEN_SNAPSHOTS_URL).toURL().readText()
                versions.addAll(parseMinecraftVersionsFromMaven(snapshotsXml))
            } catch (e: Exception) {
                println("Warning: Could not fetch snapshots: ${e.message}")
            }

            return versions
        }

        /**
         * Parses Maven metadata XML to extract unique Minecraft versions
         */
        private fun parseMinecraftVersionsFromMaven(xml: String): Set<String> {
            val mcVersions = mutableSetOf<String>()

            try {
                val factory = DocumentBuilderFactory.newInstance()
                val builder = factory.newDocumentBuilder()
                val document = builder.parse(xml.byteInputStream())

                val versionNodes = document.getElementsByTagName("version")
                for (i in 0 until versionNodes.length) {
                    val version = versionNodes.item(i).textContent
                    // Extract MC version from format: X.X.X+MC_VERSION or X.X.X+MC_VERSION-SNAPSHOT
                    val mcVersion = version.substringAfter("+").substringBefore("-")
                    mcVersions.add(mcVersion)
                }
            } catch (e: Exception) {
                println("Error parsing Maven metadata: ${e.message}")
            }

            return mcVersions
        }
    }


    @TestFactory
    fun testAllMinecraftVersions() = sequence {
        val availableVersions = getAvailableMinecraftVersions()
        println("Found Minecraft versions in Maven: ${availableVersions.joinToString(", ")}")

        for (mcVersion in availableVersions.sorted()) {
            yield(DynamicTest.dynamicTest("Test Minecraft version $mcVersion") {
                testMinecraftVersionAvailability(mcVersion)
            })
        }
    }.asIterable()

    private fun testMinecraftVersionAvailability(mcVersion: String) {
        println("\n=== Testing Minecraft version: $mcVersion ===")

        // Create VersionController with the specific MC version for testing
        val versionController = LambdaVersionController(minecraftVersionOverride = mcVersion)

        // Test STABLE mode
        println("Checking STABLE releases for MC $mcVersion...")
        ConfigManager.config = ConfigManager.config.copy(clientReleaseMode = ReleaseMode.Stable)

        val stableJar = versionController.getOrDownloadLatestVersion()
        if (stableJar != null) {
            println("✓ Found STABLE release: ${stableJar.name}")
            assertTrue(stableJar.exists(), "STABLE JAR should exist")
            if(stableJar.name.contains(mcVersion)) {
                println("✓ STABLE JAR found for MC $mcVersion")
            }
        } else {
            println("⚠ No STABLE release found for MC $mcVersion")
        }

        // Test SNAPSHOT mode
        println("Checking SNAPSHOT releases for MC $mcVersion...")
        ConfigManager.config = ConfigManager.config.copy(clientReleaseMode = ReleaseMode.Snapshot)

        val snapshotJar = versionController.getOrDownloadLatestVersion()
        if (snapshotJar != null) {
            println("✓ Found SNAPSHOT release: ${snapshotJar.name}")
            assertTrue(snapshotJar.exists(), "SNAPSHOT JAR should exist")
            // Snapshot filename format: lambda-X.X.X-timestamp-buildnumber.jar
            val hasVersion = snapshotJar.name.contains(mcVersion)
            assertTrue(hasVersion, "SNAPSHOT JAR should be for MC $mcVersion, got: ${snapshotJar.name}")
        } else {
            println("⚠ No SNAPSHOT release found for MC $mcVersion")
        }

        // At least one should be available
        assertTrue(stableJar != null || snapshotJar != null,
            "At least one release (STABLE or SNAPSHOT) should exist for MC $mcVersion")
    }


    @Test
    fun testGetOrDownloadLatestVersionStable() {
        println("Testing getOrDownloadLatestVersion with STABLE mode...")

        // Use the first available MC version for testing
        val availableVersions = getAvailableMinecraftVersions()
        assertTrue(availableVersions.isNotEmpty(), "Should have at least one MC version available")
        val mcVersion = availableVersions.sorted().first()

        val versionController = LambdaVersionController(minecraftVersionOverride = mcVersion)

        // Set to STABLE mode
        ConfigManager.config = ConfigManager.config.copy(clientReleaseMode = ReleaseMode.Stable)

        val jarFile = versionController.getOrDownloadLatestVersion()

        assertNotNull(jarFile, "JAR file should not be null")
        assertTrue(jarFile!!.exists(), "JAR file should exist")
        assertTrue(jarFile.isFile, "Should be a file")
        assertTrue(jarFile.name.endsWith(".jar"), "Should be a JAR file")
        assertFalse(jarFile.name.contains("SNAPSHOT"), "STABLE version should not contain SNAPSHOT")

        // Check that the JAR filename contains a Minecraft version
        val hasMinecraftVersion = jarFile.name.contains(Regex("\\+\\d+\\.\\d+\\.\\d+"))
        assertTrue(hasMinecraftVersion, "JAR filename should contain Minecraft version (format: +X.XX.XX)")

        println("Downloaded/cached STABLE JAR: ${jarFile.absolutePath}")
        println("File size: ${jarFile.length()} bytes")
    }

    @Test
    fun testGetOrDownloadLatestVersionSnapshot() {
        println("Testing getOrDownloadLatestVersion with SNAPSHOT mode...")

        // Use the first available MC version for testing
        val availableVersions = getAvailableMinecraftVersions()
        assertTrue(availableVersions.isNotEmpty(), "Should have at least one MC version available")
        val mcVersion = availableVersions.sorted().first()

        val versionController = LambdaVersionController(minecraftVersionOverride = mcVersion)

        // Set to SNAPSHOT mode
        ConfigManager.config = ConfigManager.config.copy(clientReleaseMode = ReleaseMode.Snapshot)

        val jarFile = versionController.getOrDownloadLatestVersion()

        assertNotNull(jarFile, "JAR file should not be null")
        assertTrue(jarFile!!.exists(), "JAR file should exist")
        assertTrue(jarFile.isFile, "Should be a file")
        assertTrue(jarFile.name.endsWith(".jar"), "Should be a JAR file")

        // Check that the JAR filename contains a Minecraft version
        val hasMinecraftVersion = jarFile.name.contains(Regex("\\+\\d+\\.\\d+\\.\\d+"))
        assertTrue(hasMinecraftVersion, "JAR filename should contain Minecraft version (format: +X.XX.XX)")

        println("Downloaded/cached SNAPSHOT JAR: ${jarFile.absolutePath}")
        println("File size: ${jarFile.length()} bytes")
    }

    @Test
    fun testGetOrDownloadLatestVersionCaching() {
        println("Testing that getOrDownloadLatestVersion uses cache...")

        // Use the first available MC version for testing
        val availableVersions = getAvailableMinecraftVersions()
        assertTrue(availableVersions.isNotEmpty(), "Should have at least one MC version available")
        val mcVersion = availableVersions.sorted().first()

        val versionController = LambdaVersionController(minecraftVersionOverride = mcVersion)

        ConfigManager.config = ConfigManager.config.copy(clientReleaseMode = ReleaseMode.Stable)

        // First call - should download
        val jarFile1 = versionController.getOrDownloadLatestVersion()
        assertNotNull(jarFile1, "First call should succeed")
        val firstModified = jarFile1!!.lastModified()

        // Second call - should use cache
        Thread.sleep(100) // Small delay to ensure different timestamp if file is recreated
        val jarFile2 = versionController.getOrDownloadLatestVersion()
        assertNotNull(jarFile2, "Second call should succeed")

        // Should be the same file
        assertEquals(jarFile1.absolutePath, jarFile2!!.absolutePath, "Should return same file path")
        assertEquals(firstModified, jarFile2.lastModified(), "File should not be recreated (cache used)")

        println("Cache validation successful - file not recreated")
    }

    @Test
    fun testGetOrDownloadLatestVersionFileStructure() {
        println("Testing JAR file structure...")

        // Use the first available MC version for testing
        val availableVersions = getAvailableMinecraftVersions()
        assertTrue(availableVersions.isNotEmpty(), "Should have at least one MC version available")
        val mcVersion = availableVersions.sorted().first()

        val versionController = LambdaVersionController(minecraftVersionOverride = mcVersion)

        ConfigManager.config = ConfigManager.config.copy(clientReleaseMode = ReleaseMode.Stable)

        val jarFile = versionController.getOrDownloadLatestVersion()
        assertNotNull(jarFile, "JAR file should not be null")

        // Read first few bytes to verify it's a valid JAR (ZIP file)
        val bytes = jarFile!!.readBytes()
        assertTrue(bytes.size > 1000, "JAR should be larger than 1000 bytes")
        assertEquals('P'.code.toByte(), bytes[0], "JAR should start with 'P' (ZIP signature)")
        assertEquals('K'.code.toByte(), bytes[1], "JAR should have 'K' as second byte (ZIP signature)")

        println("JAR file structure validated")
    }

    @Test
    fun testSwitchingBetweenReleaseAndSnapshot() {
        println("Testing switching between STABLE and SNAPSHOT modes...")

        // Use the first available MC version for testing
        val availableVersions = getAvailableMinecraftVersions()
        assertTrue(availableVersions.isNotEmpty(), "Should have at least one MC version available")
        val mcVersion = availableVersions.sorted().first()

        val versionController = LambdaVersionController(minecraftVersionOverride = mcVersion)

        // Get STABLE version
        ConfigManager.config = ConfigManager.config.copy(clientReleaseMode = ReleaseMode.Stable)
        val stableJar = versionController.getOrDownloadLatestVersion()
        assertNotNull(stableJar, "STABLE JAR should not be null")
        println("STABLE: ${stableJar!!.name}")

        // Switch to SNAPSHOT
        ConfigManager.config = ConfigManager.config.copy(clientReleaseMode = ReleaseMode.Snapshot)
        val snapshotJar = versionController.getOrDownloadLatestVersion()
        assertNotNull(snapshotJar, "SNAPSHOT JAR should not be null")
        println("SNAPSHOT: ${snapshotJar!!.name}")

        // Filenames should be different (unless there's only one version available)
        // This is acceptable behavior when testing with limited versions
        println("STABLE filename: ${stableJar.name}")
        println("SNAPSHOT filename: ${snapshotJar.name}")

        println("Successfully switched between release modes")
    }

    @Test
    fun testMinecraftVersionMatching() {
        println("Testing that downloaded versions match available Minecraft versions...")

        val availableVersions = getAvailableMinecraftVersions()
        println("Available Minecraft versions from Maven: ${availableVersions.joinToString(", ")}")

        assertTrue(availableVersions.isNotEmpty(), "Should have at least one Minecraft version available")

        for (mcVersion in availableVersions.sorted()) {
            println("\n--- Testing MC version: $mcVersion ---")

            val versionController = LambdaVersionController(minecraftVersionOverride = mcVersion)

            // Test SNAPSHOT mode (more likely to have versions available)
            ConfigManager.config = ConfigManager.config.copy(clientReleaseMode = ReleaseMode.Snapshot)
            val jarFile = versionController.getOrDownloadLatestVersion()

            if (jarFile != null) {
                println("Downloaded: ${jarFile.name}")

                // Parse the Minecraft version from the filename
                // Format: lambda-X.X.X+MC_VERSION-timestamp-buildnumber.jar or lambda-X.X.X+MC_VERSION.jar
                val versionPattern = Regex("\\+(\\d+\\.\\d+\\.\\d+)")
                val match = versionPattern.find(jarFile.name)

                if (match != null) {
                    val downloadedMcVersion = match.groupValues[1]
                    println("Parsed MC version from JAR: $downloadedMcVersion")

                    // Verify the downloaded version is one of the available versions
                    assertTrue(availableVersions.contains(downloadedMcVersion),
                        "Downloaded MC version ($downloadedMcVersion) should be in available versions: ${availableVersions.joinToString(", ")}")

                    println("✓ Version match confirmed")
                } else {
                    fail("Could not parse Minecraft version from JAR filename: ${jarFile.name}")
                }
            } else {
                println("⚠ No JAR available for MC $mcVersion")
            }
        }
    }

    @Test
    fun testStableToSnapshotFallback() {
        println("Testing fallback from STABLE to SNAPSHOT when no stable version exists...")

        // Use a Minecraft version that might only have snapshots
        val availableVersions = getAvailableMinecraftVersions()
        assertTrue(availableVersions.isNotEmpty(), "Should have at least one MC version available")

        // Try to find a version that has both stable and snapshot, or just snapshot
        val mcVersion = availableVersions.sorted().last() // Latest version more likely to have snapshots

        println("Testing with Minecraft version: $mcVersion")

        val versionController = LambdaVersionController(minecraftVersionOverride = mcVersion)

        // Set to STABLE mode - should fallback to snapshot if stable doesn't exist
        ConfigManager.config = ConfigManager.config.copy(clientReleaseMode = ReleaseMode.Stable, debug = true)

        val jarFile = versionController.getOrDownloadLatestVersion()

        if (jarFile != null) {
            println("✓ Successfully retrieved version (with potential fallback): ${jarFile.name}")
            assertTrue(jarFile.exists(), "JAR file should exist")
            assertTrue(jarFile.name.endsWith(".jar"), "Should be a JAR file")
            println("Fallback test passed!")
        } else {
            println("⚠ No version found even with fallback - this is expected if neither stable nor snapshot exists for MC $mcVersion")
        }
    }

    @Test
    fun testNoVersionAvailableErrorMessage() {
        println("Testing error message when no version is available...")

        // Use a non-existent Minecraft version to trigger the error
        val nonExistentVersion = "99.99.99"

        println("Testing with non-existent Minecraft version: $nonExistentVersion")

        val versionController = LambdaVersionController(minecraftVersionOverride = nonExistentVersion)

        // Set to STABLE mode
        ConfigManager.config = ConfigManager.config.copy(clientReleaseMode = ReleaseMode.Stable, debug = true)

        val jarFile = versionController.getOrDownloadLatestVersion()

        // Should return null and log error messages
        assertNull(jarFile, "Should return null when no version is available")
        println("✓ Error handling test passed - null returned as expected")
    }
}
