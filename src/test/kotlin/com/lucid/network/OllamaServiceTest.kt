package com.lucid.network

import com.lucid.settings.OllamaSettings
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

class OllamaServiceTest {
    private lateinit var server: MockWebServer

    @BeforeEach
    fun setup() {
        server = MockWebServer()
        server.start()
        // point settings to mock server
        val s = OllamaSettings.getInstance().state
        s.host = "http://${server.hostName}"
        s.port = server.port
        s.model = "test-model"
    }

    @AfterEach
    fun teardown() {
        server.shutdown()
    }

    @Test
    fun `sendPrompt posts to server and returns body`() {
        val body = "{\"result\":\"hello\"}"
        server.enqueue(MockResponse().setResponseCode(200).setBody(body))

        val svc = OllamaService()
        val f = svc.sendPrompt("hello")
        val res = f.get(10, TimeUnit.SECONDS)

        assertTrue(res.contains("hello"))
    }
}

