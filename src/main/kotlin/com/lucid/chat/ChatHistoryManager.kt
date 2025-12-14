package com.lucid.chat

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.security.MessageDigest

typealias HistoryRole = String
typealias HistoryMode = String?

data class StoredActionPreview(
    val snippet: String,
    val language: String,
    val typeLabel: String,
    val description: String? = null,
    val rawJson: String? = null,
    val command: String? = null,
    val actionType: String? = null
)

data class HistoryEntry(
    val role: HistoryRole,
    val text: String,
    val mode: HistoryMode = null,
    val timestamp: Long = System.currentTimeMillis(),
    val actionPreview: StoredActionPreview? = null
)

data class ChatSession(
    var id: String,
    var title: String,
    var createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis(),
    var entries: MutableList<HistoryEntry> = mutableListOf()
)

@Service(Service.Level.PROJECT)
class ChatHistoryManager(private val project: Project) {
    private val sessions = mutableListOf<ChatSession>()
    private var currentSessionId: String? = null
    private val gson = Gson()
    private var sessionsFile: File? = null

    init {
        initialize()
    }

    private fun initialize() {
        try {
            val baseDir = Paths.get(
                System.getProperty("user.home"),
                ".lucid-intellij",
                "chatHistory"
            )
            Files.createDirectories(baseDir)
            
            val workspaceKey = computeWorkspaceKey()
            sessionsFile = baseDir.resolve("${workspaceKey}_sessions.json").toFile()
            
            if (sessionsFile!!.exists()) {
                val content = sessionsFile!!.readText()
                val type = object : TypeToken<List<ChatSession>>() {}.type
                val loaded = gson.fromJson<List<ChatSession>>(content, type) ?: emptyList()
                sessions.addAll(loaded)
                
                if (sessions.isNotEmpty()) {
                    currentSessionId = sessions.maxByOrNull { it.updatedAt }?.id
                }
            }
            
            if (sessions.isEmpty()) {
                createNewSession()
            } else if (currentSessionId == null) {
                currentSessionId = sessions.first().id
            }
        } catch (e: Exception) {
            sessions.clear()
            sessionsFile = null
            createNewSession()
        }
    }

    fun getCurrentSession(): ChatSession? {
        return sessions.find { it.id == currentSessionId }
    }
    
    fun getSessions(): List<ChatSession> {
        return sessions.sortedByDescending { it.updatedAt }
    }
    
    fun getEntries(limit: Int = 200): List<HistoryEntry> {
        val currentSession = getCurrentSession() ?: return emptyList()
        val entries = currentSession.entries
        if (entries.isEmpty()) return emptyList()
        if (entries.size <= limit) return entries.toList()
        return entries.takeLast(limit)
    }

    fun appendEntry(entry: HistoryEntry) {
        try {
            if (entry.text.isBlank()) return
            
            val currentSession = getCurrentSession() ?: return
            currentSession.entries.add(entry)
            
            if (entry.role == "user" && currentSession.title == "New Chat") {
                val title = entry.text.take(50).trim()
                if (title.isNotBlank()) {
                    currentSession.title = title
                }
            }
            
            currentSession.updatedAt = System.currentTimeMillis()
            
            if (currentSession.entries.size > 1000) {
                currentSession.entries.removeAt(0)
            }
            
            saveSessions()
        } catch (e: Exception) {
        }
    }
    
    fun createNewSession(): ChatSession {
        val newSession = ChatSession(
            id = "session_${System.currentTimeMillis()}_${(0..10000).random()}",
            title = "New Chat",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        sessions.add(newSession)
        currentSessionId = newSession.id
        saveSessions()
        return newSession
    }
    
    fun switchToSession(sessionId: String): Boolean {
        val session = sessions.find { it.id == sessionId }
        if (session != null) {
            currentSessionId = sessionId
            saveSessions()
            return true
        }
        return false
    }
    
    fun deleteSession(sessionId: String): Boolean {
        val session = sessions.find { it.id == sessionId }
        if (session != null) {
            sessions.remove(session)
            if (currentSessionId == sessionId) {
                if (sessions.isNotEmpty()) {
                    currentSessionId = sessions.maxByOrNull { it.updatedAt }?.id
                } else {
                    createNewSession()
                }
            }
            saveSessions()
            return true
        }
        return false
    }

    private fun saveSessions() {
        try {
            val file = sessionsFile ?: return
            val json = gson.toJson(sessions)
            file.writeText(json)
        } catch (e: Exception) {
        }
    }

    private fun computeWorkspaceKey(): String {
        val projectPath = project.basePath ?: project.name
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(projectPath.toByteArray())
        return hash.joinToString("") { "%02x".format(it) }
    }

    companion object {
        fun getInstance(project: Project): ChatHistoryManager {
            return project.getService(ChatHistoryManager::class.java)
                ?: ChatHistoryManager(project)
        }
    }
}

