package com.safetylog.app.data.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class EncryptedPayloadTest {

    @Test
    fun testSerializationRoundTrip() {
        val payload = EncryptedPayload(
            ivBase64 = "dGVzdEl2MTIzNDU2",
            cipherTextBase64 = "c2VjdXJlQ2lwaGVyVGV4dDEyMw=="
        )
        val serialized = payload.toSerialized()
        assertEquals("dGVzdEl2MTIzNDU2:c2VjdXJlQ2lwaGVyVGV4dDEyMw==", serialized)

        val parsed = EncryptedPayload.fromSerialized(serialized)
        assertNotNull(parsed)
        assertEquals(payload.ivBase64, parsed?.ivBase64)
        assertEquals(payload.cipherTextBase64, parsed?.cipherTextBase64)
    }

    @Test
    fun testMalformedSerializationReturnsNull() {
        assertNull(EncryptedPayload.fromSerialized(""))
        assertNull(EncryptedPayload.fromSerialized("onlyOnePart"))
        assertNull(EncryptedPayload.fromSerialized("part1:part2:part3"))
    }
}
