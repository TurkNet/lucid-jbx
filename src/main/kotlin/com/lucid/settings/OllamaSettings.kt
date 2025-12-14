package com.lucid.settings

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger

data class OllamaState(
    var ollamaEndpoint: String = "http://localhost:11434",
    var modelName: String = "llama3",
    var enableInlineCompletion: Boolean = true,
    var inlineCompletionTemperature: Double = 0.2,
    var inlineCompletionDebounceMs: Int = 350,
    var inlineCompletionMaxRemoteChars: Int = 3500,
    var ollamaApiKey: String = "",
    var ollamaApiKeyHeaderName: String = "X-API-Key",
    var ollamaExtraHeaders: Map<String, String> = emptyMap(),
    var logUnmaskedHeaders: Boolean = false,
    var logUnmaskedHeadersInDev: Boolean = true,
    var enableStreamingStatus: Boolean = false,
    var host: String = "http://127.0.0.1",
    var port: Int = 11434,
    var model: String = "llama2",
    var temperature: Double = 0.2
) {
    fun getEffectiveEndpoint(): String {
        val envEndpoint = System.getenv("OLLAMA_BASE_URL")?.trim()
        return when {
            ollamaEndpoint.isNotBlank() -> ollamaEndpoint
            !envEndpoint.isNullOrBlank() -> envEndpoint
            else -> {
                val h = host.trim().removeSuffix("/")
                "$h:$port"
            }
        }
    }

    fun getEffectiveModel(): String {
        val envModel = System.getenv("OLLAMA_MODEL")?.trim()?.removeSurrounding("\"")
        return when {
            modelName.isNotBlank() -> modelName
            !envModel.isNullOrBlank() -> envModel
            else -> model
        }
    }

    fun getEffectiveApiKey(): String {
        val envApiKey = System.getenv("OLLAMA_API_KEY")?.trim()
        val settingsKey = ollamaApiKey.trim()
        
        val result = when {
            settingsKey.isNotBlank() -> {
                Logger.getInstance(OllamaSettings::class.java).info("OllamaSettings.getEffectiveApiKey: Using API key from Settings (length=${settingsKey.length})")
                settingsKey
            }
            !envApiKey.isNullOrBlank() -> {
                Logger.getInstance(OllamaSettings::class.java).info("OllamaSettings.getEffectiveApiKey: Using API key from Environment Variable OLLAMA_API_KEY (length=${envApiKey.length})")
                envApiKey
            }
            else -> {
                Logger.getInstance(OllamaSettings::class.java).warn("OllamaSettings.getEffectiveApiKey: No API key found! Settings: '${settingsKey.take(5)}...' (length=${settingsKey.length}), Env: '${envApiKey?.take(5) ?: "null"}...'")
                ""
            }
        }
        
        return result
    }

    fun getEffectiveExtraHeaders(): Map<String, String> {
        val envHeaders = System.getenv("OLLAMA_EXTRA_HEADERS")?.trim()
        val parsedEnvHeaders = if (!envHeaders.isNullOrBlank()) {
            try {
                val gson = Gson()
                val json = envHeaders.removeSurrounding("\"")
                val parsed = gson.fromJson<Map<String, String>>(json, Map::class.java) as? Map<String, String>
                parsed ?: emptyMap()
            } catch (e: Exception) {
                Logger.getInstance(OllamaSettings::class.java).warn("OllamaSettings: Failed to parse OLLAMA_EXTRA_HEADERS: $envHeaders", e)
                emptyMap()
            }
        } else {
            emptyMap()
        }
        
        return when {
            ollamaExtraHeaders.isNotEmpty() -> ollamaExtraHeaders
            parsedEnvHeaders.isNotEmpty() -> parsedEnvHeaders
            else -> emptyMap()
        }
    }

    fun getEffectiveTemperature(): Double {
        return if (inlineCompletionTemperature > 0) inlineCompletionTemperature else temperature
    }
}

@State(
    name = "OllamaSettings",
    storages = [Storage(value = "lucid-ollama-settings.xml", roamingType = RoamingType.DISABLED)]
)
class OllamaSettings : PersistentStateComponent<OllamaState> {
    private var myState = OllamaState()

    override fun getState(): OllamaState = myState
    override fun loadState(state: OllamaState) {
        this.myState = state
    }

    companion object {
        private val INSTANCE = OllamaSettings()
        fun getInstance(): OllamaSettings {
            return try {
                ApplicationManager.getApplication().getService(OllamaSettings::class.java) ?: INSTANCE
            } catch (e: Exception) {
                INSTANCE
            }
        }
    }
}
