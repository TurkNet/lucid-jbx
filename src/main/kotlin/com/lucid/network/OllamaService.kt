package com.lucid.network

import com.lucid.settings.OllamaSettings
import com.intellij.openapi.components.Service
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@Service(Service.Level.APP)
class OllamaService {
    private val executor = Executors.newCachedThreadPool()
    private val client = OkHttpClient.Builder()
        .callTimeout(60, TimeUnit.SECONDS)
        .build()

    fun sendPrompt(prompt: String): CompletableFuture<String> {
        val settings = OllamaSettings.getInstance().state
        val host = settings.host.trim().removeSuffix("/")
        val url = "$host:${settings.port}/api/generate"
        val escaped = prompt.replace("\"", "\\\"")
        val json = "{" +
                "\"model\":\"${settings.model}\"," +
                "\"prompt\":\"$escaped\"," +
                "\"temperature\":${settings.temperature}" +
                "}"

        val future = CompletableFuture<String>()
        executor.submit {
            try {
                val mediaType = "application/json; charset=utf-8".toMediaType()
                val body = json.toRequestBody(mediaType)
                val request = Request.Builder()
                    .url(url)
                    .post(body)
                    .build()

                client.newCall(request).execute().use { resp ->
                    val code = resp.code
                    val respBody = resp.body?.string() ?: ""
                    if (code in 200..299) future.complete(respBody)
                    else future.completeExceptionally(RuntimeException("HTTP $code: $respBody"))
                }
            } catch (e: Exception) {
                future.completeExceptionally(e)
            }
        }
        return future
    }
}
