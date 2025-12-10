package com.lucid.settings

import com.intellij.openapi.options.Configurable
import javax.swing.*

class OllamaSettingsConfigurable : Configurable {
    private var panel: JPanel? = null
    private var hostField: JTextField? = null
    private var portField: JTextField? = null
    private var modelField: JTextField? = null
    private var tempField: JTextField? = null

    override fun getDisplayName(): String = "Lucid Ollama"

    override fun createComponent(): JComponent? {
        if (panel == null) {
            panel = JPanel()
            panel!!.layout = BoxLayout(panel, BoxLayout.Y_AXIS)

            hostField = JTextField()
            portField = JTextField()
            modelField = JTextField()
            tempField = JTextField()

            panel!!.add(JLabel("Host (including http://):"))
            panel!!.add(hostField)
            panel!!.add(JLabel("Port:"))
            panel!!.add(portField)
            panel!!.add(JLabel("Model:"))
            panel!!.add(modelField)
            panel!!.add(JLabel("Temperature:"))
            panel!!.add(tempField)
        }
        reset()
        return panel
    }

    override fun isModified(): Boolean {
        val s = OllamaSettings.getInstance().state
        return hostField!!.text != s.host || portField!!.text != s.port.toString() || modelField!!.text != s.model || tempField!!.text != s.temperature.toString()
    }

    override fun apply() {
        val s = OllamaSettings.getInstance().state
        s.host = hostField!!.text
        s.port = portField!!.text.toIntOrNull() ?: s.port
        s.model = modelField!!.text
        s.temperature = tempField!!.text.toDoubleOrNull() ?: s.temperature
    }

    override fun reset() {
        val s = OllamaSettings.getInstance().state
        hostField?.text = s.host
        portField?.text = s.port.toString()
        modelField?.text = s.model
        tempField?.text = s.temperature.toString()
    }

    override fun disposeUIResources() {
        panel = null
        hostField = null
        portField = null
        modelField = null
        tempField = null
    }
}

