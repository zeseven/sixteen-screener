package com.sixteenscreener.app.data.remote.auth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.security.GeneralSecurityException
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64

/**
 * Service account key bundled into the APK, reduced to the fields needed to mint ID tokens.
 */
class ServiceAccountKey(
    /** Service account email, used as JWT issuer and subject. */
    val clientEmail: String,
    /** RSA private key the JWT is signed with. */
    val privateKey: PrivateKey,
    /** ID of the private key, sent as the JWT `kid` header when present. */
    val privateKeyId: String?,
    /** OAuth 2.0 token endpoint, used as JWT audience and exchange URL. */
    val tokenUri: String,
) {
    companion object {
        private const val PEM_HEADER = "-----BEGIN PRIVATE KEY-----"
        private const val PEM_FOOTER = "-----END PRIVATE KEY-----"
        private val keyJson = Json { ignoreUnknownKeys = true }

        fun parse(json: String): ServiceAccountKey {
            val fields = try {
                keyJson.decodeFromString<ServiceAccountKeyFields>(json)
            } catch (exception: SerializationException) {
                throw InvalidServiceAccountKeyException("Service account key is not valid JSON", exception)
            } catch (exception: IllegalArgumentException) {
                throw InvalidServiceAccountKeyException("Service account key is not valid JSON", exception)
            }
            return ServiceAccountKey(
                clientEmail = fields.clientEmail.requireField("client_email"),
                privateKey = parsePrivateKey(fields.privateKey.requireField("private_key")),
                privateKeyId = fields.privateKeyId?.takeIf { it.isNotBlank() },
                tokenUri = fields.tokenUri.requireField("token_uri"),
            )
        }

        private fun String?.requireField(name: String): String =
            this?.takeIf { it.isNotBlank() } ?: throw InvalidServiceAccountKeyException("Service account key has no '$name'")

        private fun parsePrivateKey(pem: String): PrivateKey {
            val trimmed = pem.trim()
            if (!trimmed.startsWith(PEM_HEADER) || !trimmed.endsWith(PEM_FOOTER)) {
                throw InvalidServiceAccountKeyException("Private key is not a PKCS#8 PEM block")
            }
            val base64Body = trimmed.removePrefix(PEM_HEADER).removeSuffix(PEM_FOOTER).filterNot(Char::isWhitespace)
            return try {
                val der = Base64.getDecoder().decode(base64Body)
                KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(der))
            } catch (exception: IllegalArgumentException) {
                throw InvalidServiceAccountKeyException("Private key is not valid Base64", exception)
            } catch (exception: GeneralSecurityException) {
                throw InvalidServiceAccountKeyException("Private key is not a valid RSA key", exception)
            }
        }
    }
}

class InvalidServiceAccountKeyException(message: String, cause: Throwable? = null) : Exception(message, cause)

@Serializable
private data class ServiceAccountKeyFields(
    @SerialName("client_email") val clientEmail: String? = null,
    @SerialName("private_key") val privateKey: String? = null,
    @SerialName("private_key_id") val privateKeyId: String? = null,
    @SerialName("token_uri") val tokenUri: String? = null,
)
