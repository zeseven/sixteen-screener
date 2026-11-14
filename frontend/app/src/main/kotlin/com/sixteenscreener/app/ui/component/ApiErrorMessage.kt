package com.sixteenscreener.app.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.stringResource
import com.sixteenscreener.app.R
import com.sixteenscreener.app.domain.model.ApiError

/** User-facing message for a failed backend call: the problem detail sent by the backend when present, a generic text otherwise. */
@Composable
@ReadOnlyComposable
fun apiErrorMessage(error: ApiError): String = when (error) {
    is ApiError.Network -> stringResource(R.string.error_network)
    ApiError.NoProfile -> stringResource(R.string.error_no_profile)
    ApiError.Unauthorized -> stringResource(R.string.error_unauthorized)
    is ApiError.NotFound -> error.problem?.detail ?: stringResource(R.string.error_not_found)
    is ApiError.Http -> error.problem?.detail ?: stringResource(R.string.error_server, error.status)
    is ApiError.Parse -> stringResource(R.string.error_parse)
}
