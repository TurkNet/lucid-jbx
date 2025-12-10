package com.lucid.core

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class OllamaClientTest {
    private lateinit var server: MockWebServer

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `sendPrompt returns body on success`() {
        val body = "{\"result\":\"hello\"}"
        server.enqueue(MockResponse().setResponseCode(200).setBody(body))

        val client = OllamaClient(server.url("").toString().trimEnd('/'))
        val future = client.sendPrompt("test-model", "hello", 0.1)
        val res = future.get()

        assertEquals(body, res)
    }
}

