package com.lucid.settings

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.options.Configurable
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.fields.ExpandableTextField
import com.intellij.util.ui.FormBuilder
import javax.swing.*

class OllamaSettingsConfigurable : Configurable {
    private var panel: JPanel? = null
    private var endpointField: JBTextField? = null
    private var modelField: JBTextField? = null
    private var apiKeyField: JBTextField? = null

    override fun getDisplayName(): String = "Lucid Chat"

    override fun createComponent(): JComponent? {
        if (panel == null) {
            endpointField = JBTextField()
            modelField = JBTextField()
            apiKeyField = JBTextField()

            panel = FormBuilder.createFormBuilder()
                .addLabeledComponent(JBLabel("API Endpoint:"), endpointField!!, 1, false)
                .addTooltip("Full URL including protocol, e.g., https://api.example.com/chat")
                .addLabeledComponent(JBLabel("Model Name:"), modelField!!, 1, false)
                .addTooltip("Model identifier, e.g., llama3, gemma-3-27b-it-int4-awq")
                .addLabeledComponent(JBLabel("API Key (optional):"), apiKeyField!!, 1, false)
                .addTooltip("Optional API key for authentication")
                .addComponentFillVertically(JPanel(), 0)
                .panel
        }
        reset()
        return panel
    }

    override fun isModified(): Boolean {
        val s = OllamaSettings.getInstance().getState()
        return endpointField!!.text != s.ollamaEndpoint ||
                modelField!!.text != s.modelName ||
                apiKeyField!!.text != s.ollamaApiKey
    }

    override fun apply() {
        val s = OllamaSettings.getInstance().getState()
        val oldEndpoint = s.ollamaEndpoint
        val oldModel = s.modelName
        val oldApiKey = s.ollamaApiKey
        
        s.ollamaEndpoint = endpointField!!.text.trim()
        s.modelName = modelField!!.text.trim()
        s.ollamaApiKey = apiKeyField!!.text.trim()
        
        val apiKeyMasked = if (s.ollamaApiKey.length > 10) {
            "${s.ollamaApiKey.take(5)}...${s.ollamaApiKey.takeLast(5)}"
        } else {
            "***masked***"
        }
        thisLogger().info("OllamaSettingsConfigurable.apply: Settings saved - Endpoint: ${s.ollamaEndpoint}, Model: ${s.modelName}, API Key (masked): $apiKeyMasked, Length: ${s.ollamaApiKey.length}")
        
        if (oldApiKey != s.ollamaApiKey) {
            thisLogger().info("OllamaSettingsConfigurable.apply: API key changed from length ${oldApiKey.length} to ${s.ollamaApiKey.length}")
        }
    }

    override fun reset() {
        val s = OllamaSettings.getInstance().getState()
        endpointField?.text = s.ollamaEndpoint
        modelField?.text = s.modelName
        apiKeyField?.text = s.ollamaApiKey
        
        val apiKeyMasked = if (s.ollamaApiKey.length > 10) {
            "${s.ollamaApiKey.take(5)}...${s.ollamaApiKey.takeLast(5)}"
        } else {
            "***masked***"
        }
        thisLogger().info("OllamaSettingsConfigurable.reset: Settings loaded - Endpoint: ${s.ollamaEndpoint}, Model: ${s.modelName}, API Key (masked): $apiKeyMasked, Length: ${s.ollamaApiKey.length}")
    }

    override fun disposeUIResources() {
        panel = null
        endpointField = null
        modelField = null
        apiKeyField = null
    }
}

