package com.safetylog.app.data.sync

import org.json.JSONObject
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Portable envelope encryption for air-gapped team packages (D-06).
 *
 * Unlike SecureTokenStorage's Android Keystore key, the AES key here is derived from a
 * passphrase with PBKDF2, so a package can be opened on a different device. The KDF salt,
 * GCM IV and ciphertext are stored in a self-describing JSON envelope.
 */
object PackageCrypto {

    const val ENVELOPE_VERSION = 3
    const val PBKDF2_ITERATIONS = 150_000
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val KEY_BITS = 256

    fun isEnvelope(content: String): Boolean = content.trimStart().startsWith("{")

    fun encrypt(plainJson: String, passphrase: String): String {
        require(passphrase.isNotBlank()) { "A package passphrase is required" }
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(IV_BYTES).also { SecureRandom().nextBytes(it) }
        val key = deriveKey(passphrase, salt)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val cipherText = cipher.doFinal(plainJson.toByteArray(Charsets.UTF_8))

        return JSONObject()
            .put("format", "safetylog-envelope")
            .put("version", ENVELOPE_VERSION)
            .put("kdf", JSONObject()
                .put("name", "PBKDF2WithHmacSHA256")
                .put("iterations", PBKDF2_ITERATIONS)
                .put("salt", Base64.getEncoder().encodeToString(salt)))
            .put("iv", Base64.getEncoder().encodeToString(iv))
            .put("ciphertext", Base64.getEncoder().encodeToString(cipherText))
            .toString()
    }

    fun decrypt(envelopeJson: String, passphrase: String): String {
        val envelope = JSONObject(envelopeJson)
        require(envelope.optString("format") == "safetylog-envelope") { "Unsupported package format" }
        val kdf = envelope.getJSONObject("kdf")
        require(kdf.optString("name") == "PBKDF2WithHmacSHA256") { "Unsupported key derivation" }
        val iterations = kdf.optInt("iterations", PBKDF2_ITERATIONS).coerceIn(10_000, 600_000)
        val salt = Base64.getDecoder().decode(kdf.getString("salt"))
        val iv = Base64.getDecoder().decode(envelope.getString("iv"))
        val cipherText = Base64.getDecoder().decode(envelope.getString("ciphertext"))

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, deriveKey(passphrase, salt, iterations), GCMParameterSpec(128, iv))
        return String(cipher.doFinal(cipherText), Charsets.UTF_8)
    }

    private fun deriveKey(passphrase: String, salt: ByteArray, iterations: Int = PBKDF2_ITERATIONS): SecretKeySpec {
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, iterations, KEY_BITS)
        val derived = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return SecretKeySpec(derived, "AES")
    }
}
