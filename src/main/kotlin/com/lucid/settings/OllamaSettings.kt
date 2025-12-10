package com.lucid.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.application.ApplicationManager

data class OllamaState(
    var host: String = "http://127.0.0.1",
    var port: Int = 11434,
    var model: String = "llama2",
    var temperature: Double = 0.2
)

@State(name = "OllamaSettings", storages = [Storage("lucid-ollama-settings.xml")])
class OllamaSettings : PersistentStateComponent<OllamaState> {
    // Make state public so callers can access the data object directly via getInstance().state
    var state = OllamaState()

    override fun getState(): OllamaState = state
    override fun loadState(state: OllamaState) { this.state = state }

    companion object {
        // Prefer the real IDE service when running inside the IDE; fall back to a simple singleton for tests/static use.
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
