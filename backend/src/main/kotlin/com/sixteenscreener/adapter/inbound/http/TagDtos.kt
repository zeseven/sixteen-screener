package com.sixteenscreener.adapter.inbound.http

import com.sixteenscreener.domain.model.Tag
import kotlinx.serialization.Serializable

@Serializable
data class TagDto(
    val icon: String,
    val text: String,
)

@Serializable
data class TagListDto(
    val tags: List<TagDto>,
)

fun Tag.toDto() = TagDto(icon = icon, text = text)

fun List<Tag>.toTagListDto() = TagListDto(map { it.toDto() })

fun TagDto.toDomain(): Tag = Tag.of(icon, text)
