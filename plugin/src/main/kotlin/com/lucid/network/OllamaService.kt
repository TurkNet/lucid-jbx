package com.lucid.network

import com.lucid.core.OllamaClient
import com.lucid.settings.OllamaSettings
import com.intellij.openapi.components.Service
import java.util.concurrent.CompletableFuture

@Service(Service.Level.APP)
class OllamaService {
    private val client: OllamaClient by lazy {
        val s = OllamaSettings.getInstance().state
        OllamaClient(s.host.trim().removeSuffix("/") + ":${s.port}")
    }

    fun sendPrompt(prompt: String): CompletableFuture<String> {
        val s = OllamaSettings.getInstance().state
        return client.sendPrompt(s.model, prompt, s.temperature)
    }
}

