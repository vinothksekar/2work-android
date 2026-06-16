package com.twowork.core

import com.twowork.core.net.SessionInterceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class SessionInterceptorTest {

    private lateinit var server: MockWebServer

    @Before fun setUp() { server = MockWebServer(); server.start() }
    @After fun tearDown() { server.shutdown() }

    @Test
    fun `mutating request carries Origin and Referer but no csrf token`() {
        server.enqueue(MockResponse().setBody("{}"))
        val client = OkHttpClient.Builder().addInterceptor(SessionInterceptor()).build()
        val request = Request.Builder()
            .url(server.url("/api/projects/123/save"))
            .post("{}".toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().close()

        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals(Config.ORIGIN, recorded.getHeader("Origin"))
        assertEquals("${Config.ORIGIN}/", recorded.getHeader("Referer"))
        assertEquals("application/json", recorded.getHeader("Accept"))
        assertNull(recorded.getHeader("x-csrf-token"))
    }

    @Test
    fun `GET request carries Origin`() {
        server.enqueue(MockResponse().setBody("[]"))
        val client = OkHttpClient.Builder().addInterceptor(SessionInterceptor()).build()
        val request = Request.Builder().url(server.url("/api/projects")).get().build()

        client.newCall(request).execute().close()

        assertEquals(Config.ORIGIN, server.takeRequest().getHeader("Origin"))
    }
}
