package com.sixteenscreener.app.data.remote.auth

import com.sixteenscreener.app.testing.FakeServiceAccount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ServiceAccountKeyTest {

    @Test
    fun `parses client email, private key, key ID and token URI`() {
        val key = ServiceAccountKey.parse(FakeServiceAccount.keyJson())

        assertEquals(FakeServiceAccount.CLIENT_EMAIL, key.clientEmail)
        assertEquals(FakeServiceAccount.PRIVATE_KEY_ID, key.privateKeyId)
        assertEquals(FakeServiceAccount.TOKEN_URI, key.tokenUri)
        assertTrue(FakeServiceAccount.keyPair.private.encoded.contentEquals(key.privateKey.encoded))
    }

    @Test
    fun `private key ID is optional`() {
        val key = ServiceAccountKey.parse(FakeServiceAccount.keyJson(privateKeyId = null))

        assertNull(key.privateKeyId)
    }

    @Test
    fun `PEM with CRLF line breaks is parsed`() {
        val pemOnOneLine = FakeServiceAccount.pemOf().replace("\n", "\r\n")

        val key = ServiceAccountKey.parse(FakeServiceAccount.keyJson(privateKey = pemOnOneLine))

        assertEquals("PKCS#8", key.privateKey.format)
    }

    @Test
    fun `missing client email is rejected`() {
        assertInvalid(FakeServiceAccount.keyJson(clientEmail = null), "client_email")
    }

    @Test
    fun `missing private key is rejected`() {
        assertInvalid(FakeServiceAccount.keyJson(privateKey = null), "private_key")
    }

    @Test
    fun `missing token URI is rejected`() {
        assertInvalid(FakeServiceAccount.keyJson(tokenUri = null), "token_uri")
    }

    @Test
    fun `blank client email is rejected`() {
        assertInvalid(FakeServiceAccount.keyJson(clientEmail = " "), "client_email")
    }

    @Test
    fun `private key without PEM markers is rejected`() {
        assertInvalid(FakeServiceAccount.keyJson(privateKey = "MIIEvQIBADANBgkqhkiG9w0BAQEFAASC"), "PEM")
    }

    @Test
    fun `private key with corrupt Base64 is rejected`() {
        val corrupt = "-----BEGIN PRIVATE KEY-----\n!!!not-base64!!!\n-----END PRIVATE KEY-----"

        assertInvalid(FakeServiceAccount.keyJson(privateKey = corrupt), "Base64")
    }

    @Test
    fun `private key with valid Base64 but no RSA key is rejected`() {
        val notAKey = "-----BEGIN PRIVATE KEY-----\nAAECAwQFBgcICQ==\n-----END PRIVATE KEY-----"

        assertInvalid(FakeServiceAccount.keyJson(privateKey = notAKey), "RSA")
    }

    @Test
    fun `truncated private key is rejected`() {
        val truncated = FakeServiceAccount.pemOf().lines().filterIndexed { index, _ -> index != 3 }.joinToString("\n")

        assertThrows(InvalidServiceAccountKeyException::class.java) {
            ServiceAccountKey.parse(FakeServiceAccount.keyJson(privateKey = truncated))
        }
    }

    @Test
    fun `malformed JSON is rejected`() {
        assertInvalid("{\"client_email\": ", "JSON")
    }

    @Test
    fun `JSON that is not an object is rejected`() {
        assertInvalid("[]", "JSON")
    }

    private fun assertInvalid(json: String, expectedInMessage: String) {
        val exception = assertThrows(InvalidServiceAccountKeyException::class.java) { ServiceAccountKey.parse(json) }
        assertTrue(exception.message.orEmpty(), exception.message.orEmpty().contains(expectedInMessage))
    }
}
