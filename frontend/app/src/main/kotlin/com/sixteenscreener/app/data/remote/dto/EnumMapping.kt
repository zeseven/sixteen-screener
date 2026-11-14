package com.sixteenscreener.app.data.remote.dto

inline fun <reified E : Enum<E>> String.toEnumOr(fallback: E): E =
    enumValues<E>().firstOrNull { it.name == this } ?: fallback
