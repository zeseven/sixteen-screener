package com.sixteenscreener.app.data.remote.dto

import com.sixteenscreener.app.domain.model.Tag
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

fun TagDto.toDomain() = Tag(icon = icon, text = text)

fun Tag.toDto() = TagDto(icon = icon, text = text)

fun TagListDto.toDomain(): List<Tag> = tags.map { it.toDomain() }

fun List<Tag>.toTagListDto() = TagListDto(map { it.toDto() })
