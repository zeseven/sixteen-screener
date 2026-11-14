package com.sixteenscreener.app.testing

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.util.Base64

object FakeServiceAccount {
    const val CLIENT_EMAIL = "sixteen-app@test-project.iam.gserviceaccount.com"
    const val PRIVATE_KEY_ID = "0123456789abcdef"
    const val TOKEN_URI = "https://oauth2.googleapis.com/token"

    val keyPair: KeyPair by lazy {
        KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    }

    fun pemOf(privateKey: PrivateKey = keyPair.private): String {
        val body = Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(privateKey.encoded)
        return "-----BEGIN PRIVATE KEY-----\n$body\n-----END PRIVATE KEY-----\n"
    }

    fun keyJson(
        clientEmail: String? = CLIENT_EMAIL,
        privateKey: String? = pemOf(),
        privateKeyId: String? = PRIVATE_KEY_ID,
        tokenUri: String? = TOKEN_URI,
    ): String = buildJsonObject {
        put("type", "service_account")
        put("project_id", "test-project")
        privateKeyId?.let { put("private_key_id", it) }
        privateKey?.let { put("private_key", it) }
        clientEmail?.let { put("client_email", it) }
        put("client_id", "123456789")
        put("auth_uri", "https://accounts.google.com/o/oauth2/auth")
        tokenUri?.let { put("token_uri", it) }
    }.toString()

    fun idToken(expiresAtEpochSecond: Long, subject: String = "token"): String {
        val encoder = Base64.getUrlEncoder().withoutPadding()
        val header = encoder.encodeToString("""{"alg":"RS256","typ":"JWT"}""".toByteArray())
        val claims: JsonObject = buildJsonObject {
            put("sub", subject)
            put("exp", expiresAtEpochSecond)
        }
        val payload = encoder.encodeToString(claims.toString().toByteArray())
        return "$header.$payload.c2lnbmF0dXJl"
    }
}
