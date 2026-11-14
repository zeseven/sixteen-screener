package com.sixteenscreener.app.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sixteenscreener.app.R
import com.sixteenscreener.app.domain.ProfileName
import com.sixteenscreener.app.domain.model.ApiError
import com.sixteenscreener.app.ui.component.apiErrorMessage
import com.sixteenscreener.app.ui.theme.SixteenTheme

object ProfileScreenTags {
    const val NAME_FIELD = "profile_name_field"
    const val CONTINUE_BUTTON = "profile_continue_button"
}

@Composable
fun ProfileScreen(
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ProfileContent(
        uiState = uiState,
        onNameChange = viewModel::onNameChange,
        onContinue = viewModel::onContinue,
        modifier = modifier,
    )
}

@Composable
fun ProfileContent(
    uiState: ProfileUiState,
    onNameChange: (String) -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .safeDrawingPadding()
                .imePadding()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            Text(text = stringResource(R.string.profile_title), style = MaterialTheme.typography.headlineMedium)
            Text(text = stringResource(R.string.profile_prompt), style = MaterialTheme.typography.bodyLarge)
            OutlinedTextField(
                value = uiState.name,
                onValueChange = onNameChange,
                label = { Text(stringResource(R.string.profile_name_label)) },
                singleLine = true,
                isError = uiState.error != null,
                supportingText = uiState.error?.let { error -> { Text(profileErrorMessage(error)) } },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onContinue() }),
                enabled = !uiState.isSubmitting,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(ProfileScreenTags.NAME_FIELD),
            )
            Button(
                onClick = onContinue,
                enabled = !uiState.isSubmitting,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(ProfileScreenTags.CONTINUE_BUTTON),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (uiState.isSubmitting) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(R.string.profile_continue))
                    }
                }
            }
        }
    }
}

@Composable
private fun profileErrorMessage(error: ProfileError): String = when (error) {
    ProfileError.NameBlank -> stringResource(R.string.profile_error_blank)
    ProfileError.NameTooLong -> stringResource(R.string.profile_error_too_long, ProfileName.MAX_LENGTH)
    is ProfileError.Request -> apiErrorMessage(error.error)
}

@Preview(showBackground = true)
@Composable
private fun ProfileContentPreview() {
    SixteenTheme(dynamicColor = false) {
        ProfileContent(
            uiState = ProfileUiState(name = "Viktor", error = ProfileError.Request(ApiError.Network(isTimeout = true))),
            onNameChange = {},
            onContinue = {},
        )
    }
}
