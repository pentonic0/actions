package com.joy.streamtv.player

import android.util.Base64

object ClearKeyUtil {
    fun buildClearKeyJson(kid: String, key: String): String {
        val kidBase64Url = normalizeKeyValue(kid)
        val keyBase64Url = normalizeKeyValue(key)
        return """{"keys":[{"kty":"oct","kid":"$kidBase64Url","k":"$keyBase64Url"}],"type":"temporary"}"""
    }

    private fun normalizeKeyValue(value: String): String {
        val trimmed = value.trim().removePrefix("0x")
        val cleanHex = trimmed.replace(Regex("[^0-9a-fA-F]"), "")
        return if (cleanHex.length == 32 && cleanHex.matches(Regex("^[0-9a-fA-F]+$"))) {
            val bytes = cleanHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        } else value.trim().replace('+', '-').replace('/', '_').trimEnd('=')
    }
}
