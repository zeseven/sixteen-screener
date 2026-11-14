package com.sixteenscreener.adapter.inbound.http

import kotlinx.serialization.Serializable

@Serializable
data class Problem(
    val type: String? = null,
    val title: String,
    val status: Int,
    val detail: String? = null,
)
