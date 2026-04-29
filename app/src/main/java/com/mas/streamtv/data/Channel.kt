package com.mas.streamplayer.data

data class DrmInfo(
    val kid: String = "",
    val key: String = "",
    val scheme: String = "clearkey",
    val licenseUrl: String? = null
)

data class Channel(
    val name: String,
    val stream: String,
    val cookie: String? = null,
    val referer: String? = null,
    val origin: String? = null,
    val userAgent: String? = null,
    val drm: DrmInfo? = null,
    val extraHeaders: Map<String, String> = emptyMap()
)
