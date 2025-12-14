package com.lucid.chat

import com.lucid.network.OllamaService
import com.lucid.settings.OllamaSettings
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger

class AskHandler(
    private val ollamaService: OllamaService,
    private val historyManager: ChatHistoryManager? = null
) {
    fun sendPrompt(
        prompt: String,
        onChunk: (String) -> Unit,
        onComplete: (String) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        val settings = OllamaSettings.getInstance().getState()
        val shouldStream = settings.enableStreamingStatus
        val endpointInfo = settings.getEffectiveEndpoint()
        val modelInfo = settings.getEffectiveModel()
        fun buildErrorMessage(error: Throwable): String {
            val details = error.message ?: "Ollama request failed"
            return "Error: $details\nEndpoint: $endpointInfo\nModel: $modelInfo"
        }

        if (shouldStream) {
            ollamaService.sendPromptStreaming(
                prompt = prompt,
                onChunk = { chunk ->
                    ApplicationManager.getApplication().invokeLater {
                        onChunk(chunk)
                    }
                },
                onComplete = { collected ->
                    ApplicationManager.getApplication().invokeLater {
                        onComplete(collected)
                        if (collected.isNotBlank()) {
                            historyManager?.appendEntry(
                                HistoryEntry(
                                    role = "assistant",
                                    text = collected,
                                    mode = "ask"
                                )
                            )
                        }
                    }
                },
                onError = { error ->
                    ApplicationManager.getApplication().invokeLater {
                        onError(RuntimeException(buildErrorMessage(error)))
                        historyManager?.appendEntry(
                            HistoryEntry(
                                role = "error",
                                text = buildErrorMessage(error),
                                mode = "ask"
                            )
                        )
                    }
                }
            )
        } else {
            thisLogger().info("AskHandler.sendPrompt: Using non-streaming mode")
            ollamaService.sendPrompt(prompt, stream = false).whenComplete { result, error ->
                ApplicationManager.getApplication().invokeLater {
                    if (error != null) {
                        thisLogger().error("AskHandler.sendPrompt: Error occurred", error)
                        onError(RuntimeException(buildErrorMessage(error)))
                        historyManager?.appendEntry(
                            HistoryEntry(
                                role = "error",
                                text = buildErrorMessage(error),
                                mode = "ask"
                            )
                        )
                    } else {
                        thisLogger().info("AskHandler.sendPrompt: Received result, length=${result.length}")
                        if (result.isBlank()) {
                            thisLogger().warn("AskHandler.sendPrompt: Result is blank!")
                        }
                        onComplete(result)
                        if (result.isNotBlank()) {
                            historyManager?.appendEntry(
                                HistoryEntry(
                                    role = "assistant",
                                    text = result,
                                    mode = "ask"
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

