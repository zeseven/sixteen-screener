package com.sixteenscreener.domain.service

import com.sixteenscreener.domain.model.Profile
import com.sixteenscreener.domain.model.ProfileId
import com.sixteenscreener.domain.model.ResourceNotFoundException
import com.sixteenscreener.port.outbound.ProfileRepository

internal suspend fun ProfileRepository.existing(id: ProfileId): Profile =
    findById(id) ?: throw profileNotFound(id)

internal fun profileNotFound(id: ProfileId) = ResourceNotFoundException("Profile '$id' not found")
