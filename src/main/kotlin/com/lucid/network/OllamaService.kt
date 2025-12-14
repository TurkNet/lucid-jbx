package com.lucid.network

import com.lucid.settings.OllamaSettings
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

data class OllamaRequest(
    val model: String,
    val messages: List<Map<String, String>>,
    val stream: Boolean = false
)

data class OllamaResponse(
    val content: String,
    val done: Boolean = false
)

@Service(Service.Level.APP)
class OllamaService {
    private val executor = Executors.newCachedThreadPool()
    private val client = OkHttpClient.Builder()
        .callTimeout(300, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .writeTimeout(300, TimeUnit.SECONDS)
        .build()

    fun buildHeaders(): Map<String, String> {
        val settings = OllamaSettings.getInstance().getState()
        val headers = mutableMapOf<String, String>()
        headers["Content-Type"] = "application/json"
        
        val rawApiKey = settings.getEffectiveApiKey()
        val apiKeyLength = rawApiKey.length
        val maskedApiKey = if (apiKeyLength > 10) {
            "${rawApiKey.take(5)}...${rawApiKey.takeLast(5)}"
        } else {
            "***masked***"
        }
        
        val apiKeySource = when {
            settings.ollamaApiKey.isNotBlank() -> "Settings"
            System.getenv("OLLAMA_API_KEY") != null -> "Environment Variable"
            else -> "None"
        }
        thisLogger().info("OllamaService.buildHeaders: API key source: $apiKeySource, length=$apiKeyLength, masked=$maskedApiKey")
        
        if (rawApiKey.isNotBlank()) {
            val headerName = settings.ollamaApiKeyHeaderName.ifEmpty { "X-API-Key" }
            var apiKeyValue = rawApiKey.trim()
            
            val finalMasked = if (apiKeyValue.length > 20) {
                "${apiKeyValue.take(15)}...${apiKeyValue.takeLast(5)}"
            } else {
                "***masked***"
            }
            thisLogger().info("OllamaService.buildHeaders: Adding header '$headerName' with value (masked): $finalMasked, length=${apiKeyValue.length}")
            thisLogger().info("OllamaService.buildHeaders: First 10 chars: ${apiKeyValue.take(10)}, Last 10 chars: ${apiKeyValue.takeLast(10)}")
            
            headers[headerName] = apiKeyValue
        } else {
            thisLogger().error("OllamaService.buildHeaders: API key is blank or empty! Settings key: '${settings.ollamaApiKey}', Env var: '${System.getenv("OLLAMA_API_KEY")}'")
        }
        
        val extraHeaders = settings.getEffectiveExtraHeaders()
        headers.putAll(extraHeaders)
        if (extraHeaders.isNotEmpty()) {
            thisLogger().info("OllamaService.buildHeaders: Added ${extraHeaders.size} extra headers: ${extraHeaders.keys.joinToString(", ")}")
        }
        
        val headerNames = headers.keys.joinToString(", ")
        thisLogger().info("OllamaService.buildHeaders: Total headers: ${headers.size} ($headerNames)")
        
        return headers
    }

    fun getEndpoint(): String {
        val settings = OllamaSettings.getInstance().getState()
        return settings.getEffectiveEndpoint().trim().removeSuffix("/")
    }

    fun getModel(): String {
        val settings = OllamaSettings.getInstance().getState()
        return settings.getEffectiveModel()
    }

    fun sendPrompt(prompt: String, stream: Boolean = false): CompletableFuture<String> {
        val settings = OllamaSettings.getInstance().getState()
        val endpoint = getEndpoint()
        val model = getModel()
        val url = if (endpoint.contains("/chat") || endpoint.contains("/api/")) {
            endpoint
        } else {
            "$endpoint/api/chat"
        }
        
        val messages = listOf(mapOf("role" to "user", "content" to prompt))
        val requestBody = mapOf(
            "model" to model,
            "messages" to messages,
            "stream" to stream
        )

        val future = CompletableFuture<String>()
        executor.submit {
            try {
                val mediaType = "application/json; charset=utf-8".toMediaType()
                val json = com.google.gson.Gson().toJson(requestBody)
                val body = json.toRequestBody(mediaType)
                
                val requestBuilder = Request.Builder()
                    .url(url)
                    .post(body)
                
                val headers = buildHeaders()
                headers.forEach { (key, value) ->
                    requestBuilder.addHeader(key, value)
                }
                
                val authHeader = headers["X-API-Key"] ?: headers["x-api-key"] ?: headers["Authorization"] ?: headers["authorization"]
                if (authHeader != null) {
                    val masked = if (authHeader.length > 20) {
                        "${authHeader.take(15)}...${authHeader.takeLast(5)}"
                    } else {
                        "***masked***"
                    }
                    thisLogger().info("OllamaService.sendPrompt: API Key header (masked): $masked, length=${authHeader.length}")
                } else {
                    thisLogger().warn("OllamaService.sendPrompt: NO API Key header found in headers!")
                }
                thisLogger().info("OllamaService.sendPrompt: Request URL: $url, Headers count: ${headers.size}")
                
                val request = requestBuilder.build()
                
                val allHeaders = request.headers
                thisLogger().info("OllamaService.sendPrompt: All request headers:")
                allHeaders.forEach { header ->
                    val headerName = header.first
                    val headerValue = header.second
                    if (headerName.equals("X-API-Key", ignoreCase = true) || 
                        headerName.equals("x-api-key", ignoreCase = true) ||
                        headerName.equals("Authorization", ignoreCase = true) || 
                        headerName.equals("authorization", ignoreCase = true)) {
                        val masked = if (headerValue.length > 20) {
                            "${headerValue.take(15)}...${headerValue.takeLast(5)}"
                        } else {
                            "***masked***"
                        }
                        thisLogger().info("  $headerName: $masked (length=${headerValue.length})")
                    } else {
                        thisLogger().info("  $headerName: $headerValue")
                    }
                }
                
                val finalApiKeyHeader = request.header("X-API-Key") ?: request.header("x-api-key") ?: 
                                       request.header("Authorization") ?: request.header("authorization")
                if (finalApiKeyHeader != null) {
                    val masked = if (finalApiKeyHeader.length > 20) {
                        "${finalApiKeyHeader.take(15)}...${finalApiKeyHeader.takeLast(5)}"
                    } else {
                        "***masked***"
                    }
                    thisLogger().info("OllamaService.sendPrompt: Final request API Key header (masked): $masked, full length=${finalApiKeyHeader.length}")
                    if (finalApiKeyHeader.length > 40) {
                        thisLogger().info("OllamaService.sendPrompt: API Key header start: ${finalApiKeyHeader.take(20)}, end: ${finalApiKeyHeader.takeLast(20)}")
                    } else {
                        thisLogger().info("OllamaService.sendPrompt: API Key header full (masked): $masked")
                    }
                } else {
                    thisLogger().error("OllamaService.sendPrompt: Final request has NO API Key header! Total headers: ${allHeaders.size}")
                }

                client.newCall(request).execute().use { resp ->
                    val code = resp.code
                    val respBody = resp.body?.string() ?: ""
                    thisLogger().info("OllamaService.sendPrompt: Response code: $code, body length: ${respBody.length}")
                    if (code in 200..299) {
                        val parsedContent = parseResponse(respBody)
                        thisLogger().info("OllamaService.sendPrompt: Parsed content length: ${parsedContent.length}")
                        future.complete(parsedContent)
                    } else {
                        val curlCommand = buildCurlCommand(request, json)
                        val errorMsg = """
HTTP $code: $respBody
Request URL: $url
Model: $model

Curl command to reproduce:
$curlCommand
""".trimIndent()
                        thisLogger().error("OllamaService.sendPrompt: Request failed with code $code")
                        future.completeExceptionally(RuntimeException(errorMsg))
                    }
                }
            } catch (e: Exception) {
                future.completeExceptionally(e)
            }
        }
        return future
    }

    fun sendPromptStreaming(
        prompt: String,
        onChunk: (String) -> Unit,
        onComplete: (String) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        val endpoint = getEndpoint()
        val model = getModel()
        val url = if (endpoint.contains("/chat") || endpoint.contains("/api/")) {
            endpoint
        } else {
            "$endpoint/api/chat"
        }
        
        val settings = OllamaSettings.getInstance().getState()
        val messages = listOf(mapOf("role" to "user", "content" to prompt))
        val requestBody = mapOf(
            "model" to model,
            "messages" to messages,
            "stream" to true
        )

        executor.submit {
            try {
                val mediaType = "application/json; charset=utf-8".toMediaType()
                val json = com.google.gson.Gson().toJson(requestBody)
                val body = json.toRequestBody(mediaType)
                
                val requestBuilder = Request.Builder()
                    .url(url)
                    .post(body)
                
                val headers = buildHeaders()
                headers.forEach { (key, value) ->
                    requestBuilder.addHeader(key, value)
                }
                
                val authHeader = headers["X-API-Key"] ?: headers["x-api-key"] ?: headers["Authorization"] ?: headers["authorization"]
                if (authHeader != null) {
                    val masked = if (authHeader.length > 20) {
                        "${authHeader.take(15)}...${authHeader.takeLast(5)}"
                    } else {
                        "***masked***"
                    }
                    thisLogger().info("OllamaService.sendPromptStreaming: API Key header (masked): $masked, length=${authHeader.length}")
                } else {
                    thisLogger().warn("OllamaService.sendPromptStreaming: NO API Key header found in headers!")
                }
                thisLogger().info("OllamaService.sendPromptStreaming: Request URL: $url, Headers count: ${headers.size}")
                
                val request = requestBuilder.build()
                
                val allHeaders = request.headers
                thisLogger().info("OllamaService.sendPromptStreaming: All request headers:")
                allHeaders.forEach { header ->
                    val headerName = header.first
                    val headerValue = header.second
                    if (headerName.equals("X-API-Key", ignoreCase = true) || 
                        headerName.equals("x-api-key", ignoreCase = true) ||
                        headerName.equals("Authorization", ignoreCase = true) || 
                        headerName.equals("authorization", ignoreCase = true)) {
                        val masked = if (headerValue.length > 20) {
                            "${headerValue.take(15)}...${headerValue.takeLast(5)}"
                        } else {
                            "***masked***"
                        }
                        thisLogger().info("  $headerName: $masked (length=${headerValue.length})")
                    } else {
                        thisLogger().info("  $headerName: $headerValue")
                    }
                }
                
                val finalApiKeyHeader = request.header("X-API-Key") ?: request.header("x-api-key") ?: 
                                       request.header("Authorization") ?: request.header("authorization")
                if (finalApiKeyHeader != null) {
                    val masked = if (finalApiKeyHeader.length > 20) {
                        "${finalApiKeyHeader.take(15)}...${finalApiKeyHeader.takeLast(5)}"
                    } else {
                        "***masked***"
                    }
                    thisLogger().info("OllamaService.sendPromptStreaming: Final request API Key header (masked): $masked, full length=${finalApiKeyHeader.length}")
                } else {
                    thisLogger().error("OllamaService.sendPromptStreaming: Final request has NO API Key header! Total headers: ${allHeaders.size}")
                }

                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        val errorBody = resp.body?.string() ?: "Unknown error"
                        val curlCommand = buildCurlCommand(request, json)
                        val errorMsg = """
HTTP ${resp.code}: $errorBody
Request URL: $url
Model: $model

Curl command to reproduce:
$curlCommand
""".trimIndent()
                        onError(RuntimeException(errorMsg))
                        return@use
                    }

                    val body = resp.body ?: return@use
                    val fullResponse = body.string()
                    thisLogger().info("OllamaService.sendPromptStreaming: Received response (length=${fullResponse.length})")
                    
                    var collected = ""
                    
                    try {
                        val jsonElement = com.google.gson.JsonParser.parseString(fullResponse)
                        if (jsonElement.isJsonObject) {
                            val parsed = jsonElement.asJsonObject
                            val content = when {
                                parsed.has("choices") && parsed.getAsJsonArray("choices").size() > 0 -> {
                                    val choice = parsed.getAsJsonArray("choices")[0].asJsonObject
                                    when {
                                        choice.has("message") && choice.getAsJsonObject("message").has("content") -> {
                                            choice.getAsJsonObject("message").get("content").asString
                                        }
                                        choice.has("text") -> choice.get("text").asString
                                        else -> {
                                            thisLogger().warn("OllamaService.sendPromptStreaming: Choice has no message.content or text")
                                            ""
                                        }
                                    }
                                }
                                parsed.has("message") && parsed.getAsJsonObject("message").has("content") -> {
                                    parsed.getAsJsonObject("message").get("content").asString
                                }
                                parsed.has("response") -> parsed.get("response").asString
                                else -> {
                                    thisLogger().warn("OllamaService.sendPromptStreaming: Unknown response format")
                                    ""
                                }
                            }
                            if (content.isNotBlank()) {
                                thisLogger().info("OllamaService.sendPromptStreaming: Extracted content (length=${content.length})")
                                onChunk(content)
                                onComplete(content)
                                return@use
                            } else {
                                thisLogger().warn("OllamaService.sendPromptStreaming: Content is blank after parsing")
                            }
                        }
                    } catch (e: Exception) {
                        thisLogger().warn("OllamaService.sendPromptStreaming: Failed to parse as single JSON, trying line-by-line", e)
                    }
                    
                    val lines = fullResponse.lines()
                    thisLogger().info("OllamaService.sendPromptStreaming: Processing ${lines.size} lines")
                    lines.forEach { line ->
                        if (line.isBlank()) return@forEach
                        try {
                            val jsonElement = com.google.gson.JsonParser.parseString(line)
                            if (jsonElement.isJsonObject) {
                                val parsed = jsonElement.asJsonObject
                                val content = when {
                                    parsed.has("choices") && parsed.getAsJsonArray("choices").size() > 0 -> {
                                        val choice = parsed.getAsJsonArray("choices")[0].asJsonObject
                                        when {
                                            choice.has("delta") && choice.getAsJsonObject("delta").has("content") -> {
                                                choice.getAsJsonObject("delta").get("content").asString
                                            }
                                            choice.has("message") && choice.getAsJsonObject("message").has("content") -> {
                                                choice.getAsJsonObject("message").get("content").asString
                                            }
                                            choice.has("text") -> choice.get("text").asString
                                            else -> ""
                                        }
                                    }
                                    parsed.has("message") && parsed.getAsJsonObject("message").has("content") -> {
                                        parsed.getAsJsonObject("message").get("content").asString
                                    }
                                    parsed.has("response") -> parsed.get("response").asString
                                    else -> ""
                                }
                                if (content.isNotBlank()) {
                                    collected += content
                                    onChunk(content)
                                }
                            }
                        } catch (e: Exception) {
                            if (line.isNotBlank()) {
                                collected += line
                                onChunk(line)
                            }
                        }
                    }
                    thisLogger().info("OllamaService.sendPromptStreaming: Completed, total collected length=${collected.length}")
                    onComplete(collected)
                }
            } catch (e: Exception) {
                onError(e)
            }
        }
    }

    private fun buildCurlCommand(request: Request, requestBodyJson: String): String {
        val url = request.url.toString()
        val method = request.method
        val headers = request.headers
        
        val curlParts = mutableListOf<String>()
        curlParts.add("curl -X $method")
        curlParts.add("\"$url\"")
        
        headers.forEach { header ->
            val headerName = header.first
            val headerValue = header.second
            val escapedValue = headerValue.replace("\"", "\\\"")
            curlParts.add("-H \"$headerName: $escapedValue\"")
        }
        
        if (requestBodyJson.isNotBlank()) {
            val escapedBody = requestBodyJson.replace("\\", "\\\\").replace("\"", "\\\"")
            curlParts.add("-d \"$escapedBody\"")
        }
        
        return curlParts.joinToString(" \\\n  ")
    }

    private fun parseResponse(body: String): String {
        return try {
            thisLogger().info("OllamaService.parseResponse: Parsing response body (length=${body.length})")
            val jsonElement = com.google.gson.JsonParser.parseString(body)
            if (jsonElement.isJsonObject) {
                val parsed = jsonElement.asJsonObject
                val result = when {
                    parsed.has("choices") && parsed.getAsJsonArray("choices").size() > 0 -> {
                        val choice = parsed.getAsJsonArray("choices")[0].asJsonObject
                        when {
                            choice.has("message") && choice.getAsJsonObject("message").has("content") -> {
                                choice.getAsJsonObject("message").get("content").asString
                            }
                            choice.has("text") -> choice.get("text").asString
                            else -> {
                                thisLogger().warn("OllamaService.parseResponse: Choice object has no message.content or text")
                                body
                            }
                        }
                    }
                    parsed.has("message") && parsed.getAsJsonObject("message").has("content") -> {
                        parsed.getAsJsonObject("message").get("content").asString
                    }
                    parsed.has("response") -> parsed.get("response").asString
                    else -> {
                        thisLogger().warn("OllamaService.parseResponse: Unknown response format, returning full body")
                        body
                    }
                }
                thisLogger().info("OllamaService.parseResponse: Extracted content length=${result.length}")
                result
            } else {
                thisLogger().warn("OllamaService.parseResponse: Response is not a JSON object")
                body
            }
        } catch (e: Exception) {
            thisLogger().error("OllamaService.parseResponse: Error parsing response", e)
            body
        }
    }
}
