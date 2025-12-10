package com.lucid.core

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class OllamaClient(private val baseUrl: String) {
    private val executor = Executors.newCachedThreadPool()
    private val client = OkHttpClient.Builder()
        .callTimeout(60, TimeUnit.SECONDS)
        .build()

    fun sendPrompt(model: String, prompt: String, temperature: Double = 0.2): CompletableFuture<String> {
        val url = "$baseUrl/api/generate"
        val escaped = prompt.replace("\"", "\\\"")
        val json = "{" +
                "\"model\":\"$model\"," +
                "\"prompt\":\"$escaped\"," +
                "\"temperature\":$temperature" +
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

