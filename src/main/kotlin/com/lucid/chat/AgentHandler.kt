package com.lucid.chat

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.AppExecutorUtil
import com.lucid.network.OllamaService
import com.lucid.settings.OllamaSettings

class AgentHandler(
    private val ollamaService: OllamaService,
    private val project: Project,
    private val historyManager: ChatHistoryManager? = null
) {
    private val logger = thisLogger()

    fun sendPrompt(
        prompt: String,
        onChunk: (String) -> Unit,
        onComplete: (String) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        val settings = OllamaSettings.getInstance().getState()
        val shouldStream = settings.enableStreamingStatus
        
        val mentionedFiles = extractMentionedFiles(prompt)
        
        ReadAction.nonBlocking<ContextInfo> {
            ApplicationManager.getApplication().runReadAction<ContextInfo> {
                buildContextFromProject(mentionedFiles)
            }
        }.finishOnUiThread(ApplicationManager.getApplication().defaultModalityState) { context ->
            val enhancedPrompt = buildAgentPrompt(prompt, context)
            
            logger.info("AgentHandler: Sending prompt with context from ${context.files.size} file(s)")
            
            if (shouldStream) {
                ollamaService.sendPromptStreaming(
                    prompt = enhancedPrompt,
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
                                        mode = "agent"
                                    )
                                )
                            }
                        }
                    },
                    onError = { error ->
                        ApplicationManager.getApplication().invokeLater {
                            val errorMsg = "Error: ${error.message ?: "Agent request failed"}"
                            onError(RuntimeException(errorMsg))
                            historyManager?.appendEntry(
                                HistoryEntry(
                                    role = "error",
                                    text = errorMsg,
                                    mode = "agent"
                                )
                            )
                        }
                    }
                )
            } else {
                ollamaService.sendPrompt(prompt = enhancedPrompt, stream = false).whenComplete { result, error ->
                    ApplicationManager.getApplication().invokeLater {
                        if (error != null) {
                            val errorMsg = "Error: ${error.message ?: "Agent request failed"}"
                            logger.error("AgentHandler: Error occurred", error)
                            onError(RuntimeException(errorMsg))
                            historyManager?.appendEntry(
                                HistoryEntry(
                                    role = "error",
                                    text = errorMsg,
                                    mode = "agent"
                                )
                            )
                        } else {
                            logger.info("AgentHandler: Received result, length=${result.length}")
                            if (result.isBlank()) {
                                logger.warn("AgentHandler: Result is blank!")
                            }
                            onComplete(result)
                            if (result.isNotBlank()) {
                                historyManager?.appendEntry(
                                    HistoryEntry(
                                        role = "assistant",
                                        text = result,
                                        mode = "agent"
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }.submit(AppExecutorUtil.getAppExecutorService())
    }

    data class FileContext(
        val path: String,
        val name: String,
        val content: String,
        val language: String? = null
    )

    data class ContextInfo(
        val files: List<FileContext>,
        val selectedText: String? = null,
        val mentionedFiles: List<String> = emptyList()
    )
    
    private fun extractMentionedFiles(prompt: String): List<String> {
        val mentioned = mutableListOf<String>()
        val regex = Regex("@([^\\s@]+)")
        regex.findAll(prompt).forEach { match ->
            val filePath = match.groupValues[1]
            if (filePath.isNotBlank()) {
                mentioned.add(filePath)
            }
        }
        return mentioned.distinct()
    }

    private fun buildContextFromProject(mentionedFiles: List<String> = emptyList()): ContextInfo {
        val fileContexts = mutableListOf<FileContext>()
        var selectedText: String? = null
        
        val fileEditorManager = FileEditorManager.getInstance(project)
        val editor = fileEditorManager.getSelectedTextEditor()
        if (editor != null && editor.selectionModel.hasSelection()) {
            selectedText = editor.selectionModel.selectedText
            logger.info("AgentHandler: Found selected text (${selectedText?.length ?: 0} chars)")
        }
        
        val mentionedFileContexts = mutableListOf<FileContext>()
        for (mentionedPath in mentionedFiles) {
            val file = findFileByPath(mentionedPath)
            if (file != null && file.isValid && !file.isDirectory) {
                try {
                    val content = readFileContent(file, 50000)
                    if (content != null) {
                        val language = detectLanguage(file)
                        val relativePath = getRelativePath(file)
                        
                        mentionedFileContexts.add(
                            FileContext(
                                path = relativePath,
                                name = file.name ?: "unknown",
                                content = content,
                                language = language
                            )
                        )
                        logger.info("AgentHandler: Added mentioned file: $relativePath")
                    }
                } catch (e: Exception) {
                    logger.warn("AgentHandler: Failed to read mentioned file $mentionedPath", e)
                }
            } else if (file != null && file.isDirectory) {
                collectFilesFromDirectory(file, mentionedFileContexts, 50000, 20)
            }
        }
        
        val rootManager = ProjectRootManager.getInstance(project)
        val contentRoots = rootManager.contentRoots
        
        val relevantExtensions = setOf(
            "kt", "java", "js", "jsx", "ts", "tsx", "py", "go", "rs", "rb", "php", "swift",
            "c", "cpp", "h", "hpp", "cs", "scala", "clj", "hs", "ml", "fs", "r", "m", "mm",
            "xml", "json", "yaml", "yml", "md", "html", "css", "scss", "sass", "less",
            "gradle", "kts", "properties", "sh", "bash", "sql", "dockerfile", "env",
            "gitignore", "gitattributes", "pom", "build", "cmake", "makefile"
        )
        
        val relevantFileNames = setOf(
            "pom.xml", "build.gradle", "build.gradle.kts", "package.json", "package-lock.json",
            "yarn.lock", "dockerfile", ".gitignore", ".env", "readme.md", "readme",
            "cmakelists.txt", "makefile", ".gitattributes", "docker-compose.yml", "docker-compose.yaml"
        )
        
        val ignoredDirs = setOf(
            ".git", ".idea", "node_modules", ".gradle", "build", "target", "out", "dist",
            ".vscode", ".vs", "__pycache__", ".pytest_cache", ".mypy_cache", "venv", "env",
            ".venv", ".tox", ".coverage", "coverage", ".nyc_output", ".cache", "tmp", "temp"
        )
        
        val maxFiles = 50
        val maxFileSize = 50000
        
        for (root in contentRoots) {
            if (fileContexts.size >= maxFiles) break
            
            try {
                scanDirectory(
                    root,
                    fileContexts,
                    relevantExtensions,
                    relevantFileNames,
                    ignoredDirs,
                    maxFileSize,
                    root.path,
                    maxFiles
                )
            } catch (e: Exception) {
                logger.warn("AgentHandler: Error scanning root ${root.path}", e)
            }
        }
        
        val finalFileContexts = mentionedFileContexts + fileContexts
        
        logger.info("AgentHandler: Collected ${finalFileContexts.size} files from project (${mentionedFileContexts.size} mentioned)")
        
        return ContextInfo(
            files = finalFileContexts,
            selectedText = selectedText,
            mentionedFiles = mentionedFiles
        )
    }
    
    private fun scanDirectory(
        directory: VirtualFile,
        fileContexts: MutableList<FileContext>,
        relevantExtensions: Set<String>,
        relevantFileNames: Set<String>,
        ignoredDirs: Set<String>,
        maxFileSize: Int,
        projectRootPath: String,
        maxFiles: Int
    ) {
        if (fileContexts.size >= maxFiles) return
        
        try {
            if (directory.isValid && directory.isDirectory) {
                val children = directory.children
                for (child in children) {
                    if (fileContexts.size >= maxFiles) break
                    
                    if (child.isDirectory) {
                        val dirName = child.name?.lowercase() ?: ""
                        if (!ignoredDirs.contains(dirName) && !dirName.startsWith(".")) {
                            scanDirectory(
                                child,
                                fileContexts,
                                relevantExtensions,
                                relevantFileNames,
                                ignoredDirs,
                                maxFileSize,
                                projectRootPath,
                                maxFiles
                            )
                        }
                    } else {
                        val fileName = child.name?.lowercase() ?: ""
                        val extension = child.extension?.lowercase() ?: ""
                        
                        val isRelevant = relevantExtensions.contains(extension) ||
                                relevantFileNames.contains(fileName) ||
                                relevantFileNames.any { fileName.contains(it.lowercase()) }
                        
                        if (isRelevant && child.isValid && !child.isDirectory) {
                            try {
                                val content = readFileContent(child, maxFileSize)
                                if (content != null) {
                                    val language = detectLanguage(child)
                                    val relativePath = child.path.removePrefix(projectRootPath).trimStart('/')
                                    
                                    fileContexts.add(
                                        FileContext(
                                            path = relativePath,
                                            name = child.name ?: "unknown",
                                            content = content,
                                            language = language
                                        )
                                    )
                                    
                                    logger.info("AgentHandler: Added project file: $relativePath (${content.length} chars)")
                                }
                            } catch (e: Exception) {
                                logger.warn("AgentHandler: Failed to read file ${child.path}", e)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logger.warn("AgentHandler: Error scanning directory ${directory.path}", e)
        }
    }
    
    private fun readFileContent(file: VirtualFile, maxSize: Int): String? {
        return try {
            val document = FileDocumentManager.getInstance().getDocument(file)
            val content = if (document != null) {
                document.text
            } else {
                file.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
            }
            
            if (content.length > maxSize) {
                content.take(maxSize) + "\n\n... (dosya çok büyük, sadece ilk ${maxSize / 1000}KB gösteriliyor)"
            } else {
                content
            }
        } catch (e: Exception) {
            logger.warn("AgentHandler: Could not read file content for ${file.name}", e)
            null
        }
    }
    
    private fun findFileByPath(path: String): VirtualFile? {
        val rootManager = ProjectRootManager.getInstance(project)
        val contentRoots = rootManager.contentRoots
        
        for (root in contentRoots) {
            val fullPath = if (path.startsWith(root.path)) {
                path
            } else {
                "${root.path}/$path".replace("//", "/")
            }
            
            val file = LocalFileSystem.getInstance().findFileByPath(fullPath)
            if (file != null && file.isValid) {
                return file
            }
        }
        
        for (root in contentRoots) {
            val file = findFileByName(root, path)
            if (file != null) {
                return file
            }
        }
        
        return null
    }
    
    private fun findFileByName(directory: VirtualFile, fileName: String): VirtualFile? {
        if (!directory.isValid || !directory.isDirectory) return null
        
        try {
            val children = directory.children
            for (child in children) {
                if (child.name?.equals(fileName, ignoreCase = true) == true) {
                    return child
                }
                if (child.isDirectory) {
                    val found = findFileByName(child, fileName)
                    if (found != null) return found
                }
            }
        } catch (e: Exception) {
        }
        
        return null
    }
    
    private fun getRelativePath(file: VirtualFile): String {
        val rootManager = ProjectRootManager.getInstance(project)
        val contentRoots = rootManager.contentRoots
        
        for (root in contentRoots) {
            val rootPath = root.path
            val filePath = file.path
            if (filePath.startsWith(rootPath)) {
                return filePath.removePrefix(rootPath).trimStart('/')
            }
        }
        
        return file.path
    }
    
    private fun collectFilesFromDirectory(
        directory: VirtualFile,
        fileContexts: MutableList<FileContext>,
        maxFileSize: Int,
        maxFiles: Int
    ) {
        if (fileContexts.size >= maxFiles) return
        
        try {
            if (!directory.isValid || !directory.isDirectory) return
            
            val children = directory.children
            for (child in children) {
                if (fileContexts.size >= maxFiles) break
                
                if (child.isDirectory) {
                    collectFilesFromDirectory(child, fileContexts, maxFileSize, maxFiles)
                } else if (child.isValid && !child.isDirectory) {
                    val content = readFileContent(child, maxFileSize)
                    if (content != null) {
                        val language = detectLanguage(child)
                        val relativePath = getRelativePath(child)
                        
                        fileContexts.add(
                            FileContext(
                                path = relativePath,
                                name = child.name ?: "unknown",
                                content = content,
                                language = language
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            logger.warn("AgentHandler: Error collecting files from directory ${directory.path}", e)
        }
    }

    private fun detectLanguage(file: VirtualFile): String? {
        val fileName = file.name?.lowercase() ?: ""
        val extension = file.extension?.lowercase() ?: ""
        
        return when {
            fileName == "pom.xml" -> "xml"
            fileName == "build.gradle" || fileName == "build.gradle.kts" -> "gradle"
            fileName == "package.json" -> "json"
            fileName == "package-lock.json" -> "json"
            fileName == "yarn.lock" -> "yaml"
            fileName == "dockerfile" || fileName.startsWith("dockerfile.") -> "dockerfile"
            fileName == ".gitignore" -> "gitignore"
            fileName == ".env" -> "env"
            fileName == "readme.md" || fileName == "readme" -> "markdown"
            extension == "kt" -> "kotlin"
            extension == "java" -> "java"
            extension == "js" || extension == "jsx" -> "javascript"
            extension == "ts" || extension == "tsx" -> "typescript"
            extension == "py" -> "python"
            extension == "xml" -> "xml"
            extension == "json" -> "json"
            extension == "yaml" || extension == "yml" -> "yaml"
            extension == "md" -> "markdown"
            extension == "html" -> "html"
            extension == "css" -> "css"
            extension == "gradle" || extension == "kts" -> "gradle"
            extension == "properties" -> "properties"
            extension == "sh" || extension == "bash" -> "bash"
            extension == "sql" -> "sql"
            extension == "go" -> "go"
            extension == "rs" -> "rust"
            extension == "rb" -> "ruby"
            extension == "php" -> "php"
            extension == "swift" -> "swift"
            extension == "c" || extension == "cpp" || extension == "h" || extension == "hpp" -> "c"
            extension.isNotBlank() -> extension
            else -> null
        }
    }

    private fun buildAgentPrompt(userPrompt: String, context: ContextInfo): String {
        val sb = StringBuilder()
        
        sb.append("Sen bir kod asistanısın. Kullanıcının proje dosyalarına ve seçili metinlerine erişimin var. ")
        sb.append("Aşağıdaki proje dosyalarının içeriğini inceleyerek kullanıcının sorusuna cevap ver.\n\n")
        sb.append("ÖNEMLİ: Cevabını mutlaka Markdown formatında ver. Eğer dosyalarda sorunlar veya düzenleme önerileri varsa, ")
        sb.append("bunları aşağıdaki formatta göster:\n\n")
        sb.append("## Sorunlar ve Öneriler\n\n")
        sb.append("Her sorun için:\n")
        sb.append("- **Sorun:** [açıklama]\n")
        sb.append("- **Konum:** [dosya adı ve satır numarası veya bölüm]\n")
        sb.append("- **Öneri:** [düzeltme önerisi]\n")
        sb.append("- **Kod Örneği:** (eğer varsa)\n")
        sb.append("```xml\n")
        sb.append("[düzeltilmiş kod örneği]\n")
        sb.append("```\n\n")
        sb.append("Eğer düzeltmeler varsa, bunları \"## Düzenleme Önerileri\" başlığı altında, her düzenleme için:\n")
        sb.append("- **Dosya:** [dosya adı]\n")
        sb.append("- **Değişiklik:** [ne değişecek]\n")
        sb.append("- **Eski Kod:**\n")
        sb.append("```xml\n")
        sb.append("[eski kod - sadece değişecek kısım]\n")
        sb.append("```\n")
        sb.append("- **Yeni Kod:**\n")
        sb.append("```xml\n")
        sb.append("[yeni kod - sadece değişecek kısım]\n")
        sb.append("```\n")
        sb.append("- **Tam Dosya (Düzeltilmiş):**\n")
        sb.append("```xml\n")
        sb.append("[TÜM DOSYA İÇERİĞİ - tüm düzeltmeler uygulanmış halde]\n")
        sb.append("```\n\n")
        sb.append("ÖNEMLİ: \"Tam Dosya (Düzeltilmiş)\" bölümünde MUTLAKA tüm dosya içeriğini, tüm düzeltmeler uygulanmış halde göster. ")
        sb.append("Bu bölüm olmadan dosya düzeltilemez. TÜM XML TAG'LERİ, TÜM SATIRLAR, TÜM İÇERİK tam olarak olmalı. ")
        sb.append("Eksik veya parça halinde dosya içeriği döndürme. Dosyanın başından sonuna kadar her şeyi dahil et.\n\n")
        
        if (context.files.isEmpty()) {
            sb.append("Not: Proje dosyaları bulunamadı. Genel bir cevap ver.\n\n")
        } else {
            if (context.mentionedFiles.isNotEmpty()) {
                val mentionedFileContexts = context.files.filter { fileContext ->
                    context.mentionedFiles.any { mentionedPath ->
                        fileContext.path.contains(mentionedPath, ignoreCase = true) ||
                        mentionedPath.contains(fileContext.path, ignoreCase = true) ||
                        fileContext.name.equals(mentionedPath, ignoreCase = true)
                    }
                }
                
                if (mentionedFileContexts.isNotEmpty()) {
                    sb.append("## Öncelikli Dosyalar (@ ile etiketlenmiş):\n\n")
                    mentionedFileContexts.forEach { fileContext ->
                        sb.append("### Dosya: ${fileContext.name} (${fileContext.path})\n")
                        if (fileContext.language != null) {
                            sb.append("Dil: ${fileContext.language}\n")
                        }
                        sb.append("```${fileContext.language ?: ""}\n")
                        sb.append(fileContext.content)
                        sb.append("\n```\n\n")
                    }
                }
            }
            
            val otherFiles = if (context.mentionedFiles.isNotEmpty()) {
                context.files.filter { fileContext ->
                    !context.mentionedFiles.any { mentionedPath ->
                        fileContext.path.contains(mentionedPath, ignoreCase = true) ||
                        mentionedPath.contains(fileContext.path, ignoreCase = true) ||
                        fileContext.name.equals(mentionedPath, ignoreCase = true)
                    }
                }
            } else {
                context.files
            }
            
            if (otherFiles.isNotEmpty()) {
                sb.append("## Diğer Proje Dosyaları:\n\n")
                otherFiles.forEach { fileContext ->
                    sb.append("### Dosya: ${fileContext.name} (${fileContext.path})\n")
                    if (fileContext.language != null) {
                        sb.append("Dil: ${fileContext.language}\n")
                    }
                    sb.append("```${fileContext.language ?: ""}\n")
                    sb.append(fileContext.content)
                    sb.append("\n```\n\n")
                }
            }
        }
        
        if (context.selectedText != null && context.selectedText.isNotBlank()) {
            sb.append("## Seçili Metin:\n\n")
            sb.append("```\n")
            sb.append(context.selectedText)
            sb.append("\n```\n\n")
        }
        
        sb.append("## Kullanıcının Sorusu:\n\n")
        sb.append(userPrompt)
        sb.append("\n\n")
        sb.append("Lütfen yukarıdaki dosya içeriklerini dikkate alarak kullanıcının sorusuna detaylı ve doğru bir cevap ver. ")
        sb.append("Cevabını mutlaka Markdown formatında, yapılandırılmış ve okunabilir şekilde ver.")
        
        return sb.toString()
    }
}
