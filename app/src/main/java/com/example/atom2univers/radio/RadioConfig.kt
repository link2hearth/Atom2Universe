package com.example.atom2univers.radio

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class RadioConfig(
    val enabled: Boolean,
    val servers: List<String>,
    val requestTimeoutMs: Long,
    val maxResults: Int,
    val hideBroken: Boolean,
    val favoritesStorageKey: String,
    val userAgent: String
) {
    companion object {
        private const val CONFIG_ASSET_PATH = "config/radio-kotlin.json"

        fun load(context: Context): RadioConfig {
            val fallback = defaultConfig()
            return try {
                val raw = context.assets.open(CONFIG_ASSET_PATH).bufferedReader().use { it.readText() }
                val json = JSONObject(raw)
                val servers = json.optJSONArray("servers")?.toStringList()?.takeIf { it.isNotEmpty() }
                    ?: fallback.servers
                val requestTimeoutMs = json.optLong("requestTimeoutMs", fallback.requestTimeoutMs)
                val maxResults = json.optInt("maxResults", fallback.maxResults)
                val favoritesStorageKey = json.optString("favoritesStorageKey", fallback.favoritesStorageKey)
                val userAgent = json.optString("userAgent", fallback.userAgent)
                RadioConfig(
                    enabled = json.optBoolean("enabled", fallback.enabled),
                    servers = servers,
                    requestTimeoutMs = requestTimeoutMs,
                    maxResults = maxResults,
                    hideBroken = json.optBoolean("hideBroken", fallback.hideBroken),
                    favoritesStorageKey = if (favoritesStorageKey.isNotBlank()) favoritesStorageKey else fallback.favoritesStorageKey,
                    userAgent = if (userAgent.isNotBlank()) userAgent else fallback.userAgent
                )
            } catch (_: Exception) {
                fallback
            }
        }

        private fun JSONArray.toStringList(): List<String> {
            val list = mutableListOf<String>()
            for (index in 0 until length()) {
                val value = optString(index).trim()
                if (value.isNotEmpty()) {
                    list.add(value)
                }
            }
            return list
        }

        private fun defaultConfig(): RadioConfig {
            return RadioConfig(
                enabled = true,
                servers = listOf(
                    "https://de1.api.radio-browser.info",
                    "https://de2.api.radio-browser.info"
                ),
                requestTimeoutMs = 8000,
                maxResults = 25,
                hideBroken = true,
                favoritesStorageKey = "radioKotlinFavorites",
                userAgent = "Atom2Univers/RadioKotlin"
            )
        }
    }
}
