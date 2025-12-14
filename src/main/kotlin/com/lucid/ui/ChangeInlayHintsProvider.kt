package com.lucid.ui

import com.intellij.codeInsight.hints.*
import com.intellij.codeInsight.hints.presentation.*
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

data class ChangeInfo(
    val lineNumber: Int,
    val oldCode: String,
    val newCode: String,
    val onKeep: () -> Unit,
    val onReject: () -> Unit
)

class ChangeInlayHintsProvider : InlayHintsProvider<NoSettings> {
    
    companion object {
        @JvmStatic
        val pendingChanges = mutableMapOf<String, MutableList<ChangeInfo>>()
        
        @JvmStatic
        fun registerChanges(filePath: String, changes: List<ChangeInfo>) {
            pendingChanges[filePath] = changes.toMutableList()
        }
        
        @JvmStatic
        fun clearChanges(filePath: String) {
            pendingChanges.remove(filePath)
        }
        
        @JvmStatic
        fun getChanges(filePath: String): List<ChangeInfo> {
            return pendingChanges[filePath] ?: emptyList()
        }
    }

    override val key: SettingsKey<NoSettings> = SettingsKey("lucid.change.hints")
    override val name: String = "Lucid Change Hints"
    override val previewText: String? = "Shows keep/reject buttons for code changes"

    override fun createSettings(): NoSettings = NoSettings()
    
    override fun createConfigurable(settings: NoSettings): ImmediateConfigurable {
        return object : ImmediateConfigurable {
            override fun createComponent(listener: ChangeListener): javax.swing.JComponent {
                return javax.swing.JLabel("Lucid Change Hints - Shows inline buttons for code changes")
            }
        }
    }

    override fun getCollectorFor(
        file: PsiFile,
        editor: Editor,
        settings: NoSettings,
        sink: InlayHintsSink
    ): InlayHintsCollector? {
        val project = file.project
        return ChangeInlayHintsCollector(editor, project)
    }
    
    private class ChangeInlayHintsCollector(
        editor: Editor,
        private val project: Project
    ) : FactoryInlayHintsCollector(editor) {
        override fun collect(element: PsiElement, editor: Editor, sink: InlayHintsSink): Boolean {
            val file = element.containingFile ?: return true
            val filePath = file.virtualFile?.path ?: return true
            
            val changes = ChangeInlayHintsProvider.getChanges(filePath)
            if (changes.isEmpty()) return true
            
            changes.forEach { change ->
                val line = change.lineNumber
                if (line >= 0 && line < editor.document.lineCount) {
                    val offset = editor.document.getLineStartOffset(line)
                    
                    val keepText = factory.text(" [✓ Keep] ")
                    val keepPresentation = factory.referenceOnHover(keepText) { _, _ ->
                        change.onKeep()
                        ChangeInlayHintsProvider.pendingChanges[filePath]?.remove(change)
                    }
                    
                    val rejectText = factory.text(" [✗ Reject] ")
                    val rejectPresentation = factory.referenceOnHover(rejectText) { _, _ ->
                        change.onReject()
                        ChangeInlayHintsProvider.pendingChanges[filePath]?.remove(change)
                    }
                    
                    val keepAllText = factory.text(" [✓✓ Keep All] ")
                    val keepAllPresentation = factory.referenceOnHover(keepAllText) { _, _ ->
                        val allChanges = ChangeInlayHintsProvider.pendingChanges[filePath] ?: return@referenceOnHover
                        allChanges.forEach { it.onKeep() }
                        ChangeInlayHintsProvider.pendingChanges[filePath]?.clear()
                    }
                    
                    val presentation = SequencePresentation(
                        listOf(keepPresentation, rejectPresentation, keepAllPresentation)
                    )
                    
                    sink.addInlineElement(offset, false, presentation, false)
                }
            }
            
            return true
        }
    }
}
