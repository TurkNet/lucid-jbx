package com.lucid.ui

import com.google.gson.Gson
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefJSQuery
import com.intellij.ui.content.ContentFactory
import com.intellij.util.ui.UIUtil
import java.awt.Color
import com.lucid.chat.ActionHandler
import com.lucid.chat.AgentHandler
import com.lucid.chat.AskHandler
import com.lucid.chat.ChatHistoryManager
import com.lucid.chat.HistoryEntry
import com.lucid.network.OllamaService
import com.lucid.settings.OllamaSettings
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile

class LucidToolWindowFactory : ToolWindowFactory {
    override fun isApplicable(project: Project): Boolean {
        return true
    }
    
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        try {
            thisLogger().info("LucidToolWindowFactory: Creating tool window content for project: ${project.name}")
            ApplicationManager.getApplication().invokeLater({
            val html = loadResourceHtml("/ui/ui.html")
            val browser = JBCefBrowser()
            browser.loadHTML(html)

            val content = ContentFactory.SERVICE.getInstance().createContent(browser.component, "", false)
            toolWindow.contentManager.addContent(content)

            val gson = Gson()

            val isDark = UIUtil.isUnderDarcula()
            val bgColor = UIUtil.getPanelBackground()
            val textColor = UIUtil.getLabelForeground()
            val borderColor = if (isDark) Color(0x515658) else Color(0xE0E0E0)
            val secondaryBgColor = UIUtil.getListBackground()
            val selectionBgColor = UIUtil.getListSelectionBackground(true)
            val selectionFgColor = UIUtil.getListSelectionForeground(true)
            val buttonBgColor = if (isDark) Color(0x3C3F41) else Color(0xE1E1E1)
            val buttonHoverBgColor = if (isDark) Color(0x4E5254) else Color(0xD0D0D0)
            val inputBgColor = UIUtil.getTextFieldBackground()
            val inputBorderColor = if (isDark) Color(0x515658) else Color(0xDDDDDD)
            val linkColor = if (isDark) Color(0x589DF6) else Color(0x0066CC)
            val errorColor = if (isDark) Color(0xFF6B68) else Color(0xCC0000)
            val successColor = if (isDark) Color(0x629755) else Color(0x2E7D32)
            
            val themeColors = gson.toJson(mapOf(
                "isDark" to isDark,
                "bgColor" to colorToHex(bgColor),
                "textColor" to colorToHex(textColor),
                "borderColor" to colorToHex(borderColor),
                "secondaryBgColor" to colorToHex(secondaryBgColor),
                "selectionBgColor" to colorToHex(selectionBgColor),
                "selectionFgColor" to colorToHex(selectionFgColor),
                "buttonBgColor" to colorToHex(buttonBgColor),
                "buttonHoverBgColor" to colorToHex(buttonHoverBgColor),
                "inputBgColor" to colorToHex(inputBgColor),
                "inputBorderColor" to colorToHex(inputBorderColor),
                "linkColor" to colorToHex(linkColor),
                "errorColor" to colorToHex(errorColor),
                "successColor" to colorToHex(successColor)
            ))
            
            browser.cefBrowser.executeJavaScript(
                "if (window.__applyTheme) window.__applyTheme($themeColors);",
                browser.cefBrowser.url,
                0
            )

            val ollamaService = ApplicationManager.getApplication().getService(OllamaService::class.java)
            val historyManager = ChatHistoryManager.getInstance(project)
            val askHandler = AskHandler(ollamaService, historyManager)
            val actionHandler = ActionHandler(ollamaService, askHandler, project, historyManager)
            val agentHandler = AgentHandler(ollamaService, project, historyManager)

            val jsQuery = JBCefJSQuery.create(browser)
            jsQuery.addHandler { requestJson: String? ->
                try {
                    val request = gson.fromJson(requestJson, Map::class.java) as? Map<*, *>
                    val type = request?.get("type") as? String
                    val prompt = request?.get("prompt") as? String ?: ""
                    val mode = request?.get("mode") as? String ?: "ask"

                    when (type) {
                        "getSessions" -> {
                            val sessions = historyManager.getSessions()
                            val currentSession = historyManager.getCurrentSession()
                            val responseJson = gson.toJson(mapOf(
                                "type" to "sessions",
                                "sessions" to sessions.map { session ->
                                    mapOf(
                                        "id" to session.id,
                                        "title" to session.title,
                                        "createdAt" to session.createdAt,
                                        "updatedAt" to session.updatedAt,
                                        "messageCount" to session.entries.size
                                    )
                                },
                                "currentSessionId" to (currentSession?.id ?: "")
                            ))
                            browser.cefBrowser.executeJavaScript(
                                "if (window.__javaResponse) window.__javaResponse($responseJson);",
                                browser.cefBrowser.url,
                                0
                            )
                        }
                        "createSession" -> {
                            val newSession = historyManager.createNewSession()
                            val responseJson = gson.toJson(mapOf(
                                "type" to "sessionCreated",
                                "session" to mapOf(
                                    "id" to newSession.id,
                                    "title" to newSession.title,
                                    "createdAt" to newSession.createdAt,
                                    "updatedAt" to newSession.updatedAt
                                )
                            ))
                            browser.cefBrowser.executeJavaScript(
                                "if (window.__javaResponse) window.__javaResponse($responseJson);",
                                browser.cefBrowser.url,
                                0
                            )
                        }
                        "switchSession" -> {
                            val sessionId = request?.get("sessionId") as? String ?: ""
                            val success = historyManager.switchToSession(sessionId)
                            if (success) {
                                val currentSession = historyManager.getCurrentSession()
                                val entries = historyManager.getEntries()
                                val responseJson = gson.toJson(mapOf(
                                    "type" to "sessionSwitched",
                                    "sessionId" to sessionId,
                                    "entries" to entries.map { entry ->
                                        mapOf(
                                            "role" to entry.role,
                                            "text" to entry.text,
                                            "mode" to entry.mode,
                                            "timestamp" to entry.timestamp
                                        )
                                    }
                                ))
                                browser.cefBrowser.executeJavaScript(
                                    "if (window.__javaResponse) window.__javaResponse($responseJson);",
                                    browser.cefBrowser.url,
                                    0
                                )
                            } else {
                                val errorJson = gson.toJson(mapOf(
                                    "type" to "error",
                                    "text" to "Session bulunamadı"
                                ))
                                browser.cefBrowser.executeJavaScript(
                                    "if (window.__javaResponse) window.__javaResponse($errorJson);",
                                    browser.cefBrowser.url,
                                    0
                                )
                            }
                        }
                        "deleteSession" -> {
                            val sessionId = request?.get("sessionId") as? String ?: ""
                            val success = historyManager.deleteSession(sessionId)
                            if (success) {
                                val responseJson = gson.toJson(mapOf(
                                    "type" to "sessionDeleted",
                                    "sessionId" to sessionId
                                ))
                                browser.cefBrowser.executeJavaScript(
                                    "if (window.__javaResponse) window.__javaResponse($responseJson);",
                                    browser.cefBrowser.url,
                                    0
                                )
                            }
                        }
                        "listFiles" -> {
                            thisLogger().info("LucidToolWindowFactory: listFiles request received")
                            ApplicationManager.getApplication().executeOnPooledThread {
                                try {
                                    val query = request?.get("query") as? String ?: ""
                                    thisLogger().info("LucidToolWindowFactory: Searching files with query: '$query'")
                                    val files = listProjectFiles(project, query)
                                    thisLogger().info("LucidToolWindowFactory: Found ${files.size} files")
                                    val responseJson = gson.toJson(mapOf(
                                        "type" to "fileList",
                                        "files" to files
                                    ))
                                    ApplicationManager.getApplication().invokeLater {
                                        thisLogger().info("LucidToolWindowFactory: Sending fileList response with ${files.size} files")
                                        browser.cefBrowser.executeJavaScript(
                                            "if (window.__javaResponse) window.__javaResponse($responseJson);",
                                            browser.cefBrowser.url,
                                            0
                                        )
                                    }
                                } catch (e: Exception) {
                                    thisLogger().error("LucidToolWindowFactory: Error listing files", e)
                                    ApplicationManager.getApplication().invokeLater {
                                        val errorJson = gson.toJson(mapOf(
                                            "type" to "error",
                                            "text" to "Dosya listesi alınamadı: ${e.message}"
                                        ))
                                        browser.cefBrowser.executeJavaScript(
                                            "if (window.__javaResponse) window.__javaResponse($errorJson);",
                                            browser.cefBrowser.url,
                                            0
                                        )
                                    }
                                }
                            }
                        }
                        "send" -> {
                            historyManager.appendEntry(
                                HistoryEntry(
                                    role = "user",
                                    text = prompt,
                                    mode = mode
                                )
                            )

                            if (mode == "agent") {
                                agentHandler.sendPrompt(
                                    prompt = prompt,
                                    onChunk = { chunk ->
                                        val chunkJson = gson.toJson(mapOf(
                                            "type" to "append",
                                            "text" to chunk,
                                            "role" to "assistant"
                                        ))
                                        browser.cefBrowser.executeJavaScript(
                                            "if (window.__javaResponse) window.__javaResponse($chunkJson);",
                                            browser.cefBrowser.url,
                                            0
                                        )
                                    },
                                    onComplete = { collected ->
                                        if (collected.isNotBlank()) {
                                            val completeJson = gson.toJson(mapOf(
                                                "type" to "append",
                                                "text" to collected,
                                                "role" to "assistant"
                                            ))
                                            browser.cefBrowser.executeJavaScript(
                                                "if (window.__javaResponse) window.__javaResponse($completeJson);",
                                                browser.cefBrowser.url,
                                                0
                                            )
                                        }
                                        val statusJson = gson.toJson(mapOf(
                                            "type" to "status",
                                            "text" to "Idle",
                                            "streaming" to false
                                        ))
                                        browser.cefBrowser.executeJavaScript(
                                            "if (window.__javaResponse) window.__javaResponse($statusJson);",
                                            browser.cefBrowser.url,
                                            0
                                        )
                                    },
                                    onError = { error ->
                                        val errorJson = gson.toJson(mapOf(
                                            "type" to "error",
                                            "text" to (error.message ?: "Unknown error")
                                        ))
                                        browser.cefBrowser.executeJavaScript(
                                            "if (window.__javaResponse) window.__javaResponse($errorJson);",
                                            browser.cefBrowser.url,
                                            0
                                        )
                                    }
                                )
                            } else {
                                askHandler.sendPrompt(
                                    prompt = prompt,
                                    onChunk = { chunk ->
                                        val chunkJson = gson.toJson(mapOf(
                                            "type" to "append",
                                            "text" to chunk,
                                            "role" to "assistant"
                                        ))
                                        browser.cefBrowser.executeJavaScript(
                                            "if (window.__javaResponse) window.__javaResponse($chunkJson);",
                                            browser.cefBrowser.url,
                                            0
                                        )
                                    },
                                    onComplete = { collected ->
                                        if (collected.isNotBlank()) {
                                            val completeJson = gson.toJson(mapOf(
                                                "type" to "append",
                                                "text" to collected,
                                                "role" to "assistant"
                                            ))
                                            browser.cefBrowser.executeJavaScript(
                                                "if (window.__javaResponse) window.__javaResponse($completeJson);",
                                                browser.cefBrowser.url,
                                                0
                                            )
                                        }
                                        val statusJson = gson.toJson(mapOf(
                                            "type" to "status",
                                            "text" to "Idle",
                                            "streaming" to false
                                        ))
                                        browser.cefBrowser.executeJavaScript(
                                            "if (window.__javaResponse) window.__javaResponse($statusJson);",
                                            browser.cefBrowser.url,
                                            0
                                        )
                                    },
                                    onError = { error ->
                                        val errorJson = gson.toJson(mapOf(
                                            "type" to "error",
                                            "text" to (error.message ?: "Unknown error")
                                        ))
                                        browser.cefBrowser.executeJavaScript(
                                            "if (window.__javaResponse) window.__javaResponse($errorJson);",
                                            browser.cefBrowser.url,
                                            0
                                        )
                                    }
                                )
                            }
                        }
                        "actionRun" -> {
                            val actionId = request?.get("actionId") as? String ?: ""
                            actionHandler.runPendingAction(
                                actionId = actionId,
                                onComplete = { summary ->
                                    val summaryJson = gson.toJson(mapOf(
                                        "type" to "append",
                                        "text" to summary,
                                        "role" to "system"
                                    ))
                                    browser.cefBrowser.executeJavaScript(
                                        "if (window.__javaResponse) window.__javaResponse($summaryJson);",
                                        browser.cefBrowser.url,
                                        0
                                    )
                                },
                                onError = { error ->
                                    val errorJson = gson.toJson(mapOf(
                                        "type" to "error",
                                        "text" to error
                                    ))
                                    browser.cefBrowser.executeJavaScript(
                                        "if (window.__javaResponse) window.__javaResponse($errorJson);",
                                        browser.cefBrowser.url,
                                        0
                                    )
                                }
                            )
                        }
                        "applyChange" -> {
                            val fileName = request?.get("file") as? String ?: ""
                            val newCode = request?.get("code") as? String ?: ""
                            val oldCode = request?.get("oldCode") as? String
                            val showInlineButtons = request?.get("showInlineButtons") as? Boolean ?: false
                            
                            ApplicationManager.getApplication().invokeLater {
                                try {
                                    val fileEditorManager = com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project)
                                    val openFiles = fileEditorManager.openFiles.toList()
                                    
                                    val normalizedFileName = fileName.trim().replace("`", "").replace("**", "").replace("*", "")
                                    
                                    thisLogger().info("AgentHandler: Looking for file: '$normalizedFileName' in ${openFiles.size} open files")
                                    openFiles.forEach { file ->
                                        thisLogger().info("AgentHandler: Open file: name='${file.name}', path='${file.path}'")
                                    }
                                    
                                    val targetFile = openFiles.find { file ->
                                        file.name == normalizedFileName ||
                                        file.name.equals(normalizedFileName, ignoreCase = true) ||
                                        file.path.endsWith(normalizedFileName, ignoreCase = true) ||
                                        file.name.contains(normalizedFileName, ignoreCase = true)
                                    }
                                    
                                    if (targetFile != null) {
                                        val document = com.intellij.openapi.fileEditor.FileDocumentManager.getInstance().getDocument(targetFile)
                                        if (document != null) {
                                            val currentContent = document.text
                                            
                                            if (showInlineButtons && oldCode != null && oldCode.isNotBlank() && currentContent.contains(oldCode)) {
                                                val oldCodeLines = oldCode.lines()
                                                val changes = mutableListOf<com.lucid.ui.ChangeInfo>()
                                                
                                                val oldCodeStartIndex = currentContent.indexOf(oldCode)
                                                if (oldCodeStartIndex >= 0) {
                                                    val startLine = document.getLineNumber(oldCodeStartIndex)
                                                    
                                                    val changeInfo = com.lucid.ui.ChangeInfo(
                                                        lineNumber = startLine,
                                                        oldCode = oldCode,
                                                        newCode = newCode,
                                                        onKeep = {
                                                            com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(project) {
                                                                val updatedContent = document.text.replace(oldCode, newCode)
                                                                document.setText(updatedContent)
                                                                thisLogger().info("AgentHandler: Applied change via inline button")
                                                            }
                                                        },
                                                        onReject = {
                                                            thisLogger().info("AgentHandler: Change rejected via inline button")
                                                        }
                                                    )
                                                    
                                                    changes.add(changeInfo)
                                                    
                                                    ChangeInlayHintsProvider.registerChanges(targetFile.path, changes)
                                                    
                                                    val successJson = gson.toJson(mapOf(
                                                        "type" to "append",
                                                        "text" to "📝 Değişiklik önerileri dosyada gösteriliyor. İlgili satırların yanındaki butonları kullanabilirsiniz.",
                                                        "role" to "system"
                                                    ))
                                                    browser.cefBrowser.executeJavaScript(
                                                        "if (window.__javaResponse) window.__javaResponse($successJson);",
                                                        browser.cefBrowser.url,
                                                        0
                                                    )
                                                } else {
                                                    com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(project) {
                                                        document.setText(newCode)
                                                    }
                                                    val successJson = gson.toJson(mapOf(
                                                        "type" to "append",
                                                        "text" to "✅ Değişiklikler ${fileName} dosyasına uygulandı.",
                                                        "role" to "system"
                                                    ))
                                                    browser.cefBrowser.executeJavaScript(
                                                        "if (window.__javaResponse) window.__javaResponse($successJson);",
                                                        browser.cefBrowser.url,
                                                        0
                                                    )
                                                }
                                            } else {
                                                com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(project) {
                                                    if (oldCode != null && oldCode.isNotBlank() && currentContent.contains(oldCode)) {
                                                        val updatedContent = currentContent.replace(oldCode, newCode)
                                                        document.setText(updatedContent)
                                                        thisLogger().info("AgentHandler: Replaced old code with new code in ${targetFile.name}")
                                                    } else {
                                                        document.setText(newCode)
                                                        thisLogger().info("AgentHandler: Replaced entire file content for ${targetFile.name}")
                                                    }
                                                }
                                                
                                                val successJson = gson.toJson(mapOf(
                                                    "type" to "append",
                                                    "text" to "✅ Değişiklikler ${fileName} dosyasına uygulandı.",
                                                    "role" to "system"
                                                ))
                                                browser.cefBrowser.executeJavaScript(
                                                    "if (window.__javaResponse) window.__javaResponse($successJson);",
                                                    browser.cefBrowser.url,
                                                    0
                                                )
                                            }
                                        } else {
                                            val errorJson = gson.toJson(mapOf(
                                                "type" to "error",
                                                "text" to "Dosya açık değil veya düzenlenemiyor: $fileName"
                                            ))
                                            browser.cefBrowser.executeJavaScript(
                                                "if (window.__javaResponse) window.__javaResponse($errorJson);",
                                                browser.cefBrowser.url,
                                                0
                                            )
                                        }
                                    } else {
                                        val errorJson = gson.toJson(mapOf(
                                            "type" to "error",
                                            "text" to "Dosya bulunamadı: $fileName. Lütfen dosyayı açın."
                                        ))
                                        browser.cefBrowser.executeJavaScript(
                                            "if (window.__javaResponse) window.__javaResponse($errorJson);",
                                            browser.cefBrowser.url,
                                            0
                                        )
                                    }
                                } catch (e: Exception) {
                                    val errorJson = gson.toJson(mapOf(
                                        "type" to "error",
                                        "text" to "Hata: ${e.message}"
                                    ))
                                    browser.cefBrowser.executeJavaScript(
                                        "if (window.__javaResponse) window.__javaResponse($errorJson);",
                                        browser.cefBrowser.url,
                                        0
                                    )
                                }
                            }
                        }
                        "replay" -> {
                            val storedMode = request?.get("mode") as? String ?: "ask"
                        }
                    }
                } catch (e: Exception) {
                    val errorJson = gson.toJson(mapOf(
                        "type" to "error",
                        "text" to (e.message ?: "Unknown error")
                    ))
                    browser.cefBrowser.executeJavaScript(
                        "if (window.__javaResponse) window.__javaResponse($errorJson);",
                        browser.cefBrowser.url,
                        0
                    )
                }
                null
            }

            val exposeScript = "window.__javaQuery = function(msg) { return ${jsQuery.inject("msg")} };"
            browser.cefBrowser.executeJavaScript(exposeScript, browser.cefBrowser.url, 0)

            browser.cefBrowser.executeJavaScript(
                "window.__javaResponse = function(obj) { if (window.handleResponse) window.handleResponse(obj); };",
                browser.cefBrowser.url,
                0
            )
            })
        } catch (e: Exception) {
            thisLogger().error("LucidToolWindowFactory: Error creating tool window content", e)
            throw e
        }
    }

    private fun buildActionInstructionPrompt(promptWithContext: String, originalPrompt: String): String {
        val rules = """
            You are Lucid, an IntelliJ automation agent. Produce ONE actionable plan per request.
            Always return a JSON object describing the plan in a fenced ```json``` block with the shape {"command": string, "args": array, "type": "terminal"|"vscode"|"clipboard", "description": string, "text"?: string}.
            If the action is a shell/terminal command, also include a fenced ```bash``` block containing ONLY the executable line so the UI can render it separately.
            When editing files, prefer built-in IntelliJ actions.
            Never ask the user to run commands manually; provide the exact command and arguments yourself.
        """.trimIndent()

        return """
            $rules
            
            If you need to reference workspace context or earlier instructions, incorporate them before producing the JSON block.
            Be concise in any natural language explanation and place it after the structured outputs.
            REQUEST CONTEXT:
            $promptWithContext
            USER PROMPT:
            $originalPrompt
        """.trimIndent()
    }

    private fun loadResourceHtml(path: String): String {
        val stream = this::class.java.getResourceAsStream(path) ?: throw IllegalStateException("Resource not found: $path")
        return stream.bufferedReader().use { it.readText() }
    }
    
    private fun colorToHex(color: Color): String {
        return String.format("#%02X%02X%02X", color.red, color.green, color.blue)
    }
    
    private fun listProjectFiles(project: Project, query: String): List<Map<String, Any>> {
        val files = mutableListOf<Map<String, Any>>()
        val rootManager = ProjectRootManager.getInstance(project)
        val contentRoots = rootManager.contentRoots
        
        val ignoredDirs = setOf(
            ".git", ".idea", "node_modules", ".gradle", "build", "target", "out", "dist",
            ".vscode", ".vs", "__pycache__", ".pytest_cache", ".mypy_cache", "venv", "env",
            ".venv", ".tox", ".coverage", "coverage", ".nyc_output", ".cache", "tmp", "temp"
        )
        
        val queryLower = query.lowercase()
        
        for (root in contentRoots) {
            collectFiles(root, root.path, files, ignoredDirs, queryLower, 0, 100)
        }
        
        val filtered = if (queryLower.isNotBlank()) {
            files.filter { 
                val path = (it["path"] as? String ?: "").lowercase()
                val name = (it["name"] as? String ?: "").lowercase()
                path.contains(queryLower) || name.contains(queryLower)
            }.sortedBy { 
                val name = (it["name"] as? String ?: "").lowercase()
                when {
                    name.startsWith(queryLower) -> 0
                    name.contains(queryLower) -> 1
                    else -> 2
                }
            }
        } else {
            files.sortedBy { it["name"] as? String ?: "" }
        }
        
        return filtered.take(50)
    }
    
    private fun collectFiles(
        file: VirtualFile,
        projectRootPath: String,
        files: MutableList<Map<String, Any>>,
        ignoredDirs: Set<String>,
        query: String,
        depth: Int,
        maxDepth: Int
    ) {
        if (depth > maxDepth || files.size >= 100) return
        
        try {
            if (!file.isValid) return
            
            if (file.isDirectory) {
                val dirName = file.name?.lowercase() ?: ""
                if (depth > 0 && (ignoredDirs.contains(dirName) || dirName.startsWith("."))) {
                    return
                }
                
                if (depth > 0) {
                    val relativePath = file.path.removePrefix(projectRootPath).trimStart('/')
                    if (relativePath.isNotBlank()) {
                        files.add(mapOf(
                            "name" to (file.name ?: ""),
                            "path" to relativePath,
                            "type" to "directory",
                            "isDirectory" to true
                        ))
                    }
                }
                
                val children = file.children
                for (child in children) {
                    if (files.size >= 100) break
                    collectFiles(child, projectRootPath, files, ignoredDirs, query, depth + 1, maxDepth)
                }
            } else {
                val relativePath = file.path.removePrefix(projectRootPath).trimStart('/')
                if (relativePath.isNotBlank()) {
                    files.add(mapOf(
                        "name" to (file.name ?: ""),
                        "path" to relativePath,
                        "type" to "file",
                        "isDirectory" to false
                    ))
                }
            }
        } catch (e: Exception) {
            thisLogger().warn("LucidToolWindowFactory: Error collecting file ${file.path}", e)
        }
    }
}
