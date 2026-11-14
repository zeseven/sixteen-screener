package com.sixteenscreener.app.data.remote.dto

import com.sixteenscreener.app.domain.model.Profile
import com.sixteenscreener.app.domain.model.ProfileId
import com.sixteenscreener.app.domain.model.SourceId
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

fun ProfileDto.toDomain() = Profile(id = ProfileId(id), name = name, defaultSource = SourceId(defaultSource))
