package com.safetylog.app.data.sync

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PackageCryptoTest {

    @Test
    fun envelopeRoundTripsWithCorrectPassphrase() {
        val payload = "{\"version\":3,\"equipment\":[{\"tagId\":\"EXT-1\"}],\"inspections\":[]}"
        val envelope = PackageCrypto.encrypt(payload, "correct horse battery staple")

        assertTrue(PackageCrypto.isEnvelope(envelope))
        val parsed = JSONObject(envelope)
        assertEquals("safetylog-envelope", parsed.getString("format"))
        assertEquals(PackageCrypto.ENVELOPE_VERSION, parsed.getInt("version"))
        assertEquals("PBKDF2WithHmacSHA256", parsed.getJSONObject("kdf").getString("name"))
        assertEquals(payload, PackageCrypto.decrypt(envelope, "correct horse battery staple"))
    }

    @Test
    fun wrongPassphraseAndTamperingAreRejected() {
        val envelope = PackageCrypto.encrypt("sensitive audit data", "right-passphrase")
        assertThrows(Exception::class.java) { PackageCrypto.decrypt(envelope, "wrong-passphrase") }

        val parsed = JSONObject(envelope)
        parsed.put("ciphertext", "AAAA")
        assertThrows(Exception::class.java) { PackageCrypto.decrypt(parsed.toString(), "right-passphrase") }
    }

    @Test
    fun legacyDeviceBoundPayloadIsNotMistakenForEnvelope() {
        assertFalse(PackageCrypto.isEnvelope("aXYxMjM0NTY3ODkwMTI=:Y2lwaGVydGV4dA=="))
        assertTrue(PackageCrypto.isEnvelope("  {\"format\":\"safetylog-envelope\"}"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun blankPassphraseIsRejected() {
        PackageCrypto.encrypt("{}", " ")
    }
}
