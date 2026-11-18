package com.safetylog.app.data.auth

import org.json.JSONObject
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * A signed authorization decision issued by the server. The app can verify it with the
 * embedded public key but cannot forge one, so local code cannot elevate its own role.
 */
data class AuthorizationSnapshot(
    val token: String,
    val email: String,
    val role: UserRole,
    val tier: SubscriptionTier,
    val effectiveTier: SubscriptionTier,
    val paymentStatus: PaymentStatus,
    val seats: Int,
    val team: List<TeamMember>,
    val expiresAtEpochSeconds: Long
) {
    val isExpired: Boolean
        get() = expiresAtEpochSeconds * 1000L <= System.currentTimeMillis()

    companion object {

        /** Verify the RS256 signature and parse the payload. Returns null for anything invalid. */
        fun parseAndVerify(token: String, publicKeySpkiBase64: String): AuthorizationSnapshot? {
            if (publicKeySpkiBase64.isBlank()) return null
            return runCatching {
                val parts = token.split('.')
                require(parts.size == 3) { "Malformed snapshot" }
                val (headerSegment, payloadSegment, signatureSegment) = parts

                val header = JSONObject(String(Base64.getUrlDecoder().decode(headerSegment), Charsets.UTF_8))
                require(header.optString("alg") == "RS256") { "Unsupported snapshot algorithm" }

                val publicKey = KeyFactory.getInstance("RSA").generatePublic(
                    X509EncodedKeySpec(Base64.getDecoder().decode(publicKeySpkiBase64))
                )
                val verifier = Signature.getInstance("SHA256withRSA").apply { initVerify(publicKey) }
                verifier.update("$headerSegment.$payloadSegment".toByteArray(Charsets.US_ASCII))
                require(verifier.verify(Base64.getUrlDecoder().decode(signatureSegment))) { "Invalid signature" }

                val payload = JSONObject(String(Base64.getUrlDecoder().decode(payloadSegment), Charsets.UTF_8))
                require(payload.optInt("v") == 1) { "Unsupported snapshot version" }

                val team = mutableListOf<TeamMember>()
                payload.optJSONArray("team")?.let { members ->
                    for (index in 0 until members.length()) {
                        val member = members.getJSONObject(index)
                        team += TeamMember(
                            id = member.optString("id"),
                            email = member.optString("email"),
                            name = member.optString("name"),
                            role = runCatching { UserRole.valueOf(member.getString("role")) }.getOrDefault(UserRole.USER),
                            addedEpochMs = member.optLong("addedEpochMs", 0L),
                            isActive = member.optBoolean("isActive", true)
                        )
                    }
                }

                AuthorizationSnapshot(
                    token = token,
                    email = payload.getString("email"),
                    role = UserRole.valueOf(payload.getString("role")),
                    tier = SubscriptionTier.valueOf(payload.getString("tier")),
                    effectiveTier = runCatching { SubscriptionTier.valueOf(payload.getString("effectiveTier")) }
                        .getOrDefault(SubscriptionTier.FREE),
                    paymentStatus = runCatching { PaymentStatus.valueOf(payload.getString("paymentStatus")) }
                        .getOrDefault(PaymentStatus.ACTIVE),
                    seats = payload.optInt("seats", 1).coerceAtLeast(1),
                    team = team,
                    expiresAtEpochSeconds = payload.getLong("exp")
                )
            }.getOrNull()
        }
    }
}
