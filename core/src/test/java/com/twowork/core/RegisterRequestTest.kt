package com.twowork.core

import com.twowork.core.model.RegisterRequest
import com.twowork.core.net.Network
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression guard: the server validates registration with z.literal(true) for
 * termsAccepted/privacyAccepted, so the request body MUST carry both flags even
 * though they equal their defaults (the shared Json uses encodeDefaults=false).
 */
class RegisterRequestTest {

    @Test
    fun `register body always includes terms and privacy acceptance`() {
        val body = Network.json.encodeToString(
            RegisterRequest.serializer(),
            RegisterRequest("jane@example.com", "longenoughpw12", "Jane Doe", "freelancer")
        )
        assertTrue("termsAccepted must be serialized", body.contains("\"termsAccepted\":true"))
        assertTrue("privacyAccepted must be serialized", body.contains("\"privacyAccepted\":true"))
    }
}
