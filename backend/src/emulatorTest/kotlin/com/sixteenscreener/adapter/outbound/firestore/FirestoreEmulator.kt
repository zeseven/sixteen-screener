package com.sixteenscreener.adapter.outbound.firestore

import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.FirestoreOptions
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.UUID

class FirestoreEmulator : AutoCloseable {
    private val host: String = checkNotNull(System.getenv("FIRESTORE_EMULATOR_HOST")) {
        "FIRESTORE_EMULATOR_HOST is not set"
    }
    private val projectId = "emulator-test-${UUID.randomUUID()}"
    private val http = HttpClient.newHttpClient()

    val firestore: Firestore = FirestoreOptions.newBuilder()
        .setProjectId(projectId)
        .setEmulatorHost(host)
        .build()
        .service

    fun reset(): Firestore {
        val request = HttpRequest.newBuilder(URI("http://$host/emulator/v1/projects/$projectId/databases/(default)/documents"))
            .DELETE()
            .build()
        val response = http.send(request, HttpResponse.BodyHandlers.discarding())
        check(response.statusCode() == 200) { "Emulator reset failed with status ${response.statusCode()}" }
        return firestore
    }

    override fun close() {
        firestore.close()
        http.close()
    }
}
