package com.sixteenscreener.adapter.inbound.http

import com.sixteenscreener.domain.model.DefaultSources
import com.sixteenscreener.port.inbound.ProfileSettings
import kotlinx.serialization.Serializable

@Serializable
data class ProfileCreateDto(
    val name: String,
)

@Serializable
data class DefaultSourcesDto(
    val search: String,
    val us: String,
    val international: String,
)

@Serializable
data class ProfileDto(
    val id: String,
    val name: String,
    val defaultSources: DefaultSourcesDto,
)

fun DefaultSources.toDto() = DefaultSourcesDto(search = search.value, us = us.value, international = international.value)

fun ProfileSettings.toDto() = ProfileDto(id = id.toString(), name = name.value, defaultSources = defaultSources.toDto())
