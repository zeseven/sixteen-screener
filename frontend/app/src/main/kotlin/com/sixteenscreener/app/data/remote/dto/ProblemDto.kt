package com.sixteenscreener.app.data.remote.dto

import com.sixteenscreener.app.domain.model.ProblemDetails
import kotlinx.serialization.Serializable

@Serializable
data class ProblemDto(
    val type: String? = null,
    val title: String,
    val status: Int,
    val detail: String? = null,
)

fun ProblemDto.toDomain() = ProblemDetails(type = type, title = title, status = status, detail = detail)
