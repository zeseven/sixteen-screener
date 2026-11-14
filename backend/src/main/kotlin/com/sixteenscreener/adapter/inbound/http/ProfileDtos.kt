package com.sixteenscreener.adapter.inbound.http

import com.sixteenscreener.port.inbound.ProfileSettings
import kotlinx.serialization.Serializable

@Serializable
data class ProfileCreateDto(
    val name: String,
)

@Serializable
data class ProfileDto(
    val id: String,
    val name: String,
    val defaultSource: String,
)

fun ProfileSettings.toDto() = ProfileDto(id = id.toString(), name = name.value, defaultSource = defaultSource.value)
