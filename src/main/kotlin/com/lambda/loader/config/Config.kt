package com.lambda.loader.config

data class Config(
    val clientReleaseMode: ReleaseMode = ReleaseMode.Snapshot,
    val loaderReleaseMode: ReleaseMode = ReleaseMode.Stable,
    val debug: Boolean = false
)

enum class ReleaseMode {
    Stable,
    Snapshot
}
