package com.sixteenscreener.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sixteenscreener.app.R
import com.sixteenscreener.app.domain.NumberFormats
import com.sixteenscreener.app.domain.model.AppInfo
import com.sixteenscreener.app.ui.component.BackTopAppBar
import com.sixteenscreener.app.ui.profile.profileErrorMessage
import com.sixteenscreener.app.ui.theme.SixteenTheme

object SettingsScreenTags {
    const val CURRENT_PROFILE = "settings_current_profile"
    const val NAME_FIELD = "settings_name_field"
    const val SWITCH_BUTTON = "settings_switch_button"
    const val VERSION = "settings_version"
    const val BACKEND_URL = "settings_backend_url"
    const val SECTION_TITLE = "settings_section_title"
}

/** Settings screen; [onProfileSwitched] is called once another profile has been selected. */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onProfileSwitched: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnProfileSwitched by rememberUpdatedState(onProfileSwitched)
    LaunchedEffect(uiState.isSwitched) {
        if (uiState.isSwitched) {
            viewModel.onSwitchHandled()
            currentOnProfileSwitched()
        }
    }
    SettingsContent(
        uiState = uiState,
        onBack = onBack,
        onNewProfileNameChange = viewModel::onNewProfileNameChange,
        onSwitchProfile = viewModel::onSwitchProfile,
        modifier = modifier,
    )
}

@Composable
fun SettingsContent(
    uiState: SettingsUiState,
    onBack: () -> Unit,
    onNewProfileNameChange: (String) -> Unit,
    onSwitchProfile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = { BackTopAppBar(title = stringResource(R.string.settings_title), onBack = onBack) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionTitle(stringResource(R.string.settings_profile_section))
            InfoItem(
                label = stringResource(R.string.settings_current_profile),
                value = uiState.currentProfileName ?: NumberFormats.MISSING,
                valueTag = SettingsScreenTags.CURRENT_PROFILE,
            )
            ProfileSwitch(uiState, onNewProfileNameChange, onSwitchProfile)
            HorizontalDivider()
            SectionTitle(stringResource(R.string.settings_app_section))
            InfoItem(stringResource(R.string.settings_version), uiState.appInfo.version, SettingsScreenTags.VERSION)
            InfoItem(stringResource(R.string.settings_backend_url), uiState.appInfo.backendUrl, SettingsScreenTags.BACKEND_URL)
        }
    }
}

@Composable
private fun ProfileSwitch(
    uiState: SettingsUiState,
    onNewProfileNameChange: (String) -> Unit,
    onSwitchProfile: () -> Unit,
) {
    Text(
        text = stringResource(R.string.settings_switch_hint),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    OutlinedTextField(
        value = uiState.newProfileName,
        onValueChange = onNewProfileNameChange,
        label = { Text(stringResource(R.string.profile_name_label)) },
        singleLine = true,
        isError = uiState.error != null,
        supportingText = uiState.error?.let { error -> { Text(profileErrorMessage(error)) } },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onSwitchProfile() }),
        enabled = !uiState.isSwitching,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(SettingsScreenTags.NAME_FIELD),
    )
    Button(
        onClick = onSwitchProfile,
        enabled = !uiState.isSwitching,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(SettingsScreenTags.SWITCH_BUTTON),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (uiState.isSwitching) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Text(stringResource(R.string.settings_switch_profile))
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .semantics { heading() }
            .testTag(SettingsScreenTags.SECTION_TITLE),
    )
}

@Composable
private fun InfoItem(label: String, value: String, valueTag: String) {
    Column(modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.testTag(valueTag))
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsContentPreview() {
    SixteenTheme(dynamicColor = false) {
        SettingsContent(
            uiState = SettingsUiState(
                currentProfileName = "Viktor",
                appInfo = AppInfo(version = "0.1.0", backendUrl = "https://backend.example"),
            ),
            onBack = {},
            onNewProfileNameChange = {},
            onSwitchProfile = {},
        )
    }
}
