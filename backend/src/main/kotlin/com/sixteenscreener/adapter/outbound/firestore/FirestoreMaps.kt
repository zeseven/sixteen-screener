package com.sixteenscreener.adapter.outbound.firestore

typealias FirestoreMap = Map<String, Any?>

fun FirestoreMap.string(key: String): String = checkNotNull(stringOrNull(key)) { "Missing string '$key'" }

fun FirestoreMap.stringOrNull(key: String): String? = this[key] as String?

fun FirestoreMap.double(key: String): Double = checkNotNull(doubleOrNull(key)) { "Missing number '$key'" }

fun FirestoreMap.doubleOrNull(key: String): Double? = (this[key] as Number?)?.toDouble()

fun FirestoreMap.long(key: String): Long = checkNotNull(longOrNull(key)) { "Missing number '$key'" }

fun FirestoreMap.longOrNull(key: String): Long? = (this[key] as Number?)?.toLong()

@Suppress("UNCHECKED_CAST")
fun FirestoreMap.maps(key: String): List<FirestoreMap> = (this[key] as List<FirestoreMap>?).orEmpty()

@Suppress("UNCHECKED_CAST")
fun Any?.asFirestoreMap(): FirestoreMap = checkNotNull(this as FirestoreMap?) { "Missing map" }
