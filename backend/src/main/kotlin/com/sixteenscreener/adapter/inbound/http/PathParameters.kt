package com.sixteenscreener.adapter.inbound.http

import com.sixteenscreener.domain.model.InvalidInputException
import com.sixteenscreener.domain.model.ProfileId
import com.sixteenscreener.domain.model.SourceId
import com.sixteenscreener.domain.model.Symbol
import io.ktor.server.application.ApplicationCall
import io.ktor.server.util.getOrFail

fun ApplicationCall.profileIdParameter(): ProfileId = ProfileId.of(parameters.getOrFail("profileId"))

fun ApplicationCall.symbolParameter(): Symbol = Symbol.of(parameters.getOrFail("symbol"))

fun ApplicationCall.symbolsParameter(): List<Symbol> =
    request.queryParameters.getOrFail("symbols").split(',').map(Symbol::of)

fun ApplicationCall.sourceParameter(): SourceId? = request.queryParameters["source"]?.let(SourceId::of)

fun ApplicationCall.refreshParameter(): Boolean =
    request.queryParameters["refresh"]?.let { raw ->
        raw.toBooleanStrictOrNull() ?: throw InvalidInputException("Invalid refresh value '$raw', expected true or false")
    } ?: false
