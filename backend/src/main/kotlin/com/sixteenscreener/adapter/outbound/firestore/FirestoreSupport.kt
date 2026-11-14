package com.sixteenscreener.adapter.outbound.firestore

import com.google.api.core.ApiFuture
import com.google.api.core.ApiFutureCallback
import com.google.api.core.ApiFutures
import com.google.cloud.Timestamp
import com.google.cloud.firestore.DocumentSnapshot
import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.FirestoreOptions
import com.google.cloud.firestore.Transaction
import com.google.cloud.firestore.TransactionOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.time.Instant
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object FirestoreCollections {
    const val PROFILES = "profiles"
    const val PROFILE_NAMES = "profileNames"
    const val SOURCE_DATA = "sourceData"
    const val QUOTE_HISTORY = "quoteHistory"
    const val REQUEST_COUNTERS = "requestCounters"
    const val SOURCE_CALLS = "sourceCalls"
    const val LOGO_MISSES = "logoMisses"
    const val SEC_CIK = "secCik"
}

object FirestoreClientFactory {
    fun create(projectId: String): Firestore =
        FirestoreOptions.newBuilder().setProjectId(projectId).build().service
}

private val directExecutor = Executor(Runnable::run)

suspend fun <T> ApiFuture<T>.await(): T = suspendCancellableCoroutine { continuation ->
    ApiFutures.addCallback(
        this,
        object : ApiFutureCallback<T> {
            override fun onSuccess(result: T) = continuation.resume(result)

            override fun onFailure(error: Throwable) = continuation.resumeWithException(error)
        },
        directExecutor,
    )
    continuation.invokeOnCancellation { cancel(false) }
}

private const val TRANSACTION_ATTEMPTS = 10

private val transactionOptions: TransactionOptions =
    TransactionOptions.createReadWriteOptionsBuilder().setNumberOfAttempts(TRANSACTION_ATTEMPTS).build()

suspend fun <T> Firestore.transaction(body: (Transaction) -> T): T = runTransaction({ body(it) }, transactionOptions).await()

fun Instant.toTimestamp(): Timestamp = Timestamp.ofTimeSecondsAndNanos(epochSecond, nano)

fun Timestamp.toInstant(): Instant = Instant.ofEpochSecond(seconds, nanos.toLong())

fun DocumentSnapshot.requiredInstant(field: String): Instant =
    checkNotNull(getTimestamp(field)) { "Document ${reference.path} has no timestamp '$field'" }.toInstant()

fun DocumentSnapshot.requiredString(field: String): String =
    checkNotNull(getString(field)) { "Document ${reference.path} has no string '$field'" }
