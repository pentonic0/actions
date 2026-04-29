package com.mas.streamplayer.data

data class AppSettings(
    val autoPip: Boolean = false,
    val landscapeMode: Boolean = false,
    val resumePlaying: Boolean = false,
    val seekDurationSeconds: Int = 10,
    val startAsMute: Boolean = false
)
