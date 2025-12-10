package com.lucid.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.content.ContentFactory
import com.intellij.util.ui.UIUtil
import com.lucid.network.OllamaService
import com.intellij.ui.jcef.JBCefJSQuery
import com.intellij.openapi.application.ApplicationManager

class LucidToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        UIUtil.invokeLaterIfNeeded {
            val html = loadResourceHtml("/ui/ui.html")
            val browser = JBCefBrowser()
            browser.loadHTML(html)

            val content = ContentFactory.SERVICE.getInstance().createContent(browser.component, "Lucid Ollama", false)
            toolWindow.contentManager.addContent(content)

            val jsQuery = JBCefJSQuery.create(browser)
            jsQuery.addHandler { request: String? ->
                val req = request ?: ""
                val service = ApplicationManager.getApplication().getService(OllamaService::class.java) ?: OllamaService()
                val future = service.sendPrompt(req)
                future.whenComplete { result, err ->
                    val payload = if (err != null) {
                        mapOf("error" to (err.message ?: "unknown"))
                    } else {
                        mapOf("result" to (result ?: ""))
                    }
                    val safe = payload.entries.joinToString(prefix = "{", postfix = "}") { (k, v) ->
                        "\"${k}\":\"${v.toString().replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")}\""
                    }
                    browser.cefBrowser.executeJavaScript("window.__javaResponse(${safe});", browser.cefBrowser.url, 0)
                }
                null
            }
            val exposeScript = "window.__javaQuery = function(msg) { return ${jsQuery.inject("msg")} };"
            browser.cefBrowser.executeJavaScript(exposeScript, browser.cefBrowser.url, 0)
        }
    }

    private fun loadResourceHtml(path: String): String {
        val stream = this::class.java.getResourceAsStream(path) ?: throw IllegalStateException("Resource not found: $path")
        return stream.bufferedReader().use { it.readText() }
    }
}

