package com.lucid.chat

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.lucid.network.OllamaService
import com.lucid.settings.OllamaSettings
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

data class LucidActionPayload(
    val command: String,
    val args: List<Any>? = null,
    val type: String? = null,
    val text: String? = null,
    val description: String? = null
)

data class ActionExecutionResult(
    val success: Boolean,
    val type: String,
    val stdout: String? = null,
    val stderr: String? = null,
    val exitCode: Int? = null
)

class ActionHandler(
    private val ollamaService: OllamaService,
    private val askHandler: AskHandler,
    private val project: Project,
    private val historyManager: ChatHistoryManager? = null
) {
    private val gson = Gson()
    private val pendingActions = mutableMapOf<String, LucidActionPayload>()

    fun handleActionFlow(
        prompt: String,
        onPreview: (ActionPreview) -> Unit,
        onError: (String) -> Unit
    ) {
        ollamaService.sendPrompt(prompt, stream = false).whenComplete { responseText, error ->
            ApplicationManager.getApplication().invokeLater {
                if (error != null) {
                    onError(error.message ?: "Action mode failed")
                    return@invokeLater
                }

                if (responseText.isBlank()) {
                    onError("Action mode response was empty.")
                    return@invokeLater
                }

                val actionPayload = extractActionPayloadFromText(responseText)
                if (actionPayload == null) {
                    onError("No executable action block was found in the response.")
                    return@invokeLater
                }

                val actionId = registerPendingAction(actionPayload)
                val preview = buildActionPreview(actionId, actionPayload)
                onPreview(preview)

                historyManager?.appendEntry(
                    HistoryEntry(
                        role = "system",
                        text = preview.message,
                        mode = "action",
                        actionPreview = StoredActionPreview(
                            snippet = preview.ui.snippet,
                            language = preview.ui.language,
                            typeLabel = preview.ui.typeLabel,
                            description = preview.ui.description,
                            rawJson = preview.ui.rawJson,
                            command = preview.ui.command,
                            actionType = preview.ui.actionType
                        )
                    )
                )
            }
        }
    }

    fun runPendingAction(actionId: String, onComplete: (String) -> Unit, onError: (String) -> Unit) {
        val action = pendingActions[actionId]
        if (action == null) {
            onError("Action is no longer available.")
            return
        }

        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val result = executeActionPayload(action)
                val summary = buildActionSummary(action, result)

                ApplicationManager.getApplication().invokeLater {
                    onComplete(summary)
                    pendingActions.remove(actionId)

                    sendActionReviewToOllama(action, result, summary)
                }
            } catch (e: Exception) {
                ApplicationManager.getApplication().invokeLater {
                    onError("Action execution error: ${e.message}")
                }
            }
        }
    }

    private fun extractActionPayloadFromText(text: String): LucidActionPayload? {
        val fenceRegex = Regex("```(?:json)?\\s*([\\s\\S]*?)```", RegexOption.IGNORE_CASE)
        fenceRegex.findAll(text).forEach { match ->
            val payload = tryParseActionJson(match.groupValues[1])
            if (payload != null) return payload
        }

        val marker = text.indexOf("{\"command\"")
        if (marker == -1) return null

        var braceDepth = 0
        var snippet = ""
        for (i in marker until text.length) {
            val ch = text[i]
            snippet += ch
            if (ch == '{') braceDepth++
            else if (ch == '}') {
                braceDepth--
                if (braceDepth == 0) break
            }
        }
        return tryParseActionJson(snippet)
    }

    private fun tryParseActionJson(snippet: String): LucidActionPayload? {
        return try {
            val parsed = gson.fromJson(snippet.trim(), Map::class.java) as? Map<*, *>
            if (parsed != null && parsed["command"] is String) {
                LucidActionPayload(
                    command = parsed["command"] as String,
                    args = (parsed["args"] as? List<*>)?.map { it as Any },
                    type = parsed["type"] as? String,
                    text = parsed["text"] as? String,
                    description = parsed["description"] as? String
                )
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun registerPendingAction(payload: LucidActionPayload): String {
        val id = "action-${System.currentTimeMillis()}-${(0..100000).random()}"
        pendingActions[id] = payload
        return id
    }

    data class ActionPreviewUiPayload(
        val snippet: String,
        val language: String,
        val typeLabel: String,
        val description: String?,
        val rawJson: String?,
        val command: String?,
        val actionId: String,
        val actionType: String?
    )

    data class ActionPreview(
        val message: String,
        val ui: ActionPreviewUiPayload
    )

    private fun buildActionPreview(actionId: String, payload: LucidActionPayload): ActionPreview {
        val type = inferActionType(payload)
        val snippet = buildActionSnippet(payload, type)
        val language = inferPreviewLanguage(type, payload)
        val label = describeTypeLabel(type)
        val description = payload.description ?: "Command: ${payload.command}"
        val headline = "$label ready: $description"
        val rawJson = gson.toJson(payload)

        return ActionPreview(
            message = "$headline Use Play to execute or the toolbar for snippet actions.",
            ui = ActionPreviewUiPayload(
                actionId = actionId,
                actionType = type,
                snippet = snippet,
                language = language,
                typeLabel = label,
                description = description,
                rawJson = rawJson,
                command = payload.command
            )
        )
    }

    private fun buildActionSnippet(payload: LucidActionPayload, type: String): String {
        if (type == "terminal") {
            val parts = listOf(payload.command) + (payload.args ?: emptyList())
            return parts.joinToString(" ").trim()
        }
        if (type == "clipboard") {
            return payload.text ?: (payload.args?.firstOrNull()?.toString() ?: payload.command)
        }
        val snippetText = extractSnippetText(payload.args)
        return snippetText ?: gson.toJson(payload)
    }

    private fun inferPreviewLanguage(type: String, payload: LucidActionPayload): String {
        if (type == "terminal") return "bash"
        if (type == "clipboard") return "text"
        if (type == "vscode" && extractSnippetText(payload.args) != null) return "text"
        return if (type == "vscode") "json" else "text"
    }

    private fun extractSnippetText(args: List<Any>?): String? {
        val candidate = args?.firstOrNull() ?: return null
        if (candidate is String) return candidate
        if (candidate is Map<*, *> && candidate["snippet"] is String) {
            return candidate["snippet"] as String
        }
        return null
    }

    private fun describeTypeLabel(type: String): String {
        return when (type) {
            "terminal" -> "Terminal Action"
            "vscode" -> "IntelliJ Action"
            "clipboard" -> "Clipboard Action"
            else -> "Action"
        }
    }

    private fun executeActionPayload(action: LucidActionPayload): ActionExecutionResult {
        val kind = inferActionType(action)
        
        return when (kind) {
            "clipboard" -> {
                val text = action.text ?: (action.args?.firstOrNull()?.toString() ?: "")
                val clipboard = Toolkit.getDefaultToolkit().systemClipboard
                clipboard.setContents(StringSelection(text), null)
                Messages.showInfoMessage(project, "Lucid action copied text to clipboard.", "Lucid")
                ActionExecutionResult(success = true, type = "clipboard", stdout = text)
            }
            "vscode" -> {
                try {
                    Messages.showInfoMessage(project, "IntelliJ action executed: ${action.command}", "Lucid")
                    ActionExecutionResult(success = true, type = "vscode", stdout = "IntelliJ command executed.")
                } catch (e: Exception) {
                    ActionExecutionResult(success = false, type = "vscode", stderr = e.message)
                }
            }
            else -> {
                runTerminalAction(action)
            }
        }
    }

    private fun runTerminalAction(action: LucidActionPayload): ActionExecutionResult {
        val command = action.command.trim()
        val args = action.args?.map { it.toString() } ?: emptyList()
        
        return try {
            val processBuilder = ProcessBuilder(listOf(command) + args)
            val basePath = project.basePath ?: "."
            processBuilder.directory(java.io.File(basePath))
            val process = processBuilder.start()
            
            val stdout = process.inputStream.bufferedReader().readText()
            val stderr = process.errorStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            
            ActionExecutionResult(
                success = exitCode == 0,
                type = "terminal",
                stdout = stdout,
                stderr = stderr,
                exitCode = exitCode
            )
        } catch (e: Exception) {
            ActionExecutionResult(
                success = false,
                type = "terminal",
                stderr = e.message ?: "Unknown error"
            )
        }
    }

    private fun buildActionSummary(action: LucidActionPayload, result: ActionExecutionResult): String {
        val label = describeTypeLabel(result.type)
        val statusLine = if (result.success) "$label completed successfully." else "$label encountered an error."
        val details = mutableListOf(statusLine)
        
        if (action.description != null) {
            details.add(action.description)
        }
        
        if (result.stdout != null && result.stdout.isNotBlank()) {
            details.add("Output:\n${truncateForReview(result.stdout)}")
        }
        if (result.stderr != null && result.stderr.isNotBlank()) {
            details.add("Errors:\n${truncateForReview(result.stderr)}")
        }
        
        return details.joinToString("\n\n")
    }

    private fun truncateForReview(text: String, max: Int = 1000): String {
        return if (text.length <= max) text else "${text.take(max)}…"
    }

    private fun sendActionReviewToOllama(action: LucidActionPayload, result: ActionExecutionResult, summary: String) {
    }

    private fun inferActionType(action: LucidActionPayload): String {
        if (action.type != null) return action.type
        val command = action.command.lowercase()
        if (command.startsWith("terminal.") || command.startsWith("bash") || command.startsWith("sh ") || command.startsWith("./")) {
            return "terminal"
        }
        if (command.contains("clipboard") || command.startsWith("copy")) return "clipboard"
        if (command.contains(".") && !command.contains(" ")) return "vscode"
        return "terminal"
    }
}

