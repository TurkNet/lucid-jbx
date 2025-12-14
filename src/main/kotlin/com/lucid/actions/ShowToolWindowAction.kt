package com.lucid.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.RegisterToolWindowTask
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowManager
import com.lucid.ui.LucidToolWindowFactory

class ShowToolWindowAction : AnAction() {
    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.project != null
    }
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: run {
            thisLogger().warn("ShowToolWindowAction: Project is null")
            return
        }
        
        val manager = ToolWindowManager.getInstance(project)

        val toolWindow = manager.getToolWindow("LucidAI") ?: run {
            thisLogger().warn("ShowToolWindowAction: Tool window 'LucidAI' not found. Registering it on the fly.")
            manager.registerToolWindow(
                RegisterToolWindowTask(
                    id = "LucidAI",
                    anchor = ToolWindowAnchor.RIGHT,
                    canCloseContent = false
                )
            )
        }

        if (toolWindow == null) {
            thisLogger().error("ShowToolWindowAction: Tool window 'LucidAI' still null after registration attempt")
            return
        }

        if (toolWindow.contentManager.contents.isEmpty()) {
            try {
                LucidToolWindowFactory().createToolWindowContent(project, toolWindow)
            } catch (ex: Exception) {
                thisLogger().error("ShowToolWindowAction: Failed to create tool window content", ex)
                return
            }
        }

        toolWindow.activate {
            thisLogger().info("ShowToolWindowAction: Tool window activated successfully")
        }
    }
}

