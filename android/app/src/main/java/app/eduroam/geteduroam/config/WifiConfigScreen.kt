package app.eduroam.geteduroam.config

import android.content.Intent
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.eduroam.geteduroam.EduTopAppBar
import app.eduroam.geteduroam.R
import app.eduroam.geteduroam.config.model.EAPIdentityProviderList
import app.eduroam.geteduroam.ui.PrimaryButton
import app.eduroam.geteduroam.ui.theme.AppTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import kotlinx.coroutines.launch

@Composable
fun WifiConfigScreen(
    viewModel: WifiConfigViewModel,
    closeApp: () -> Unit,
    snackbarHostState: SnackbarHostState = SnackbarHostState(),
) = EduTopAppBar(withBackIcon = false) { paddingValues ->
    val launch by viewModel.launch.collectAsState(null)
    val processing by viewModel.processing.collectAsState(true)
    val message by viewModel.progressMessage.collectAsState("")
    val suggestionIntent by viewModel.intentWithSuggestions.collectAsState(null)
    val askNetworkPermission by viewModel.requestChangeNetworkPermission.collectAsState(
        false
    )
    val context = LocalContext.current
    launch?.let {
        LaunchedEffect(it) {
            viewModel.launchConfiguration(context)
        }
    }
    val activityLauncher = rememberLauncherForSuggestionIntent(snackbarHostState, viewModel)
    suggestionIntent?.let { intent ->
        LaunchedEffect(intent) {
            viewModel.consumeSuggestionIntent()
            activityLauncher.launch(intent)
        }
    }

    Column(
        modifier = Modifier
            .padding(paddingValues)
            .fillMaxSize()
            .systemBarsPadding()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))
        if (processing) {
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(id = R.string.configuration_progress),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth()
            )
        } else if (message.isNotEmpty()) {
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(id = R.string.configuration_logs),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                modifier = Modifier.fillMaxWidth(),
                text = message,
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ){
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = stringResource(R.string.content_description_success_checkmark),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(width = 48.dp, height = 48.dp)
                )
                Spacer(modifier = Modifier.size(16.dp))
                Text(
                    text = stringResource(id = R.string.configuration_success),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            Spacer(modifier = Modifier.weight(weight = 1f))
            PrimaryButton(
                text = stringResource(id = R.string.button_close_app),
                onClick = {
                    closeApp()
                })
            Spacer(modifier = Modifier.size(24.dp))
        }
        if (askNetworkPermission) {
            AskForWiFiPermissions { viewModel.handleAndroid10WifiConfig(context) }
        }
    }
}

@Composable
private fun rememberLauncherForSuggestionIntent(
    snackbarHostState: SnackbarHostState, viewModel: WifiConfigViewModel,
): ManagedActivityResultLauncher<Intent, WifiConfigResponse> {
    val coroutineScope = rememberCoroutineScope()
    val cancel = stringResource(R.string.configuration_progress)
    val completed = stringResource(R.string.configuration_completed)
    return rememberLauncherForActivityResult(
        contract = WifiConfigResult(),
    ) { result ->
        when (result) {
            WifiConfigResponse.Canceled -> {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(cancel)
                }
            }

            is WifiConfigResponse.Success -> {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(completed)
                }
            }
        }
        viewModel.markAsComplete()
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun AskForWiFiPermissions(
    onPermissionGranted: () -> Unit,
) {
    val multiplePermissionsState = rememberMultiplePermissionsState(
        listOf(
            android.Manifest.permission.CHANGE_WIFI_STATE,
            android.Manifest.permission.ACCESS_WIFI_STATE,
        )
    )
    if (multiplePermissionsState.allPermissionsGranted) {
        onPermissionGranted()
    } else {
        Column {
            val textToShow = getTextToShowGivenPermissions(
                multiplePermissionsState.revokedPermissions,
                multiplePermissionsState.shouldShowRationale
            )
            Text(textToShow)
            Button(onClick = { multiplePermissionsState.launchMultiplePermissionRequest() }) {
                Text("Grant permission")
            }
        }
    }
}


@OptIn(ExperimentalPermissionsApi::class)
private fun getTextToShowGivenPermissions(
    permissions: List<PermissionState>, shouldShowRationale: Boolean,
): String {
    val revokedPermissionsSize = permissions.size
    if (revokedPermissionsSize == 0) return ""

    val textToShow = StringBuilder().apply {
        append("The ")
    }

    for (i in permissions.indices) {
        textToShow.append(permissions[i].permission)
        when {
            revokedPermissionsSize > 1 && i == revokedPermissionsSize - 2 -> {
                textToShow.append(", and ")
            }

            i == revokedPermissionsSize - 1 -> {
                textToShow.append(" ")
            }

            else -> {
                textToShow.append(", ")
            }
        }
    }
    textToShow.append(if (revokedPermissionsSize == 1) "permission is" else "permissions are")
    textToShow.append(
        if (shouldShowRationale) {
            " important. Please grant all of them for the app to function properly."
        } else {
            " denied. The app cannot function without them."
        }
    )
    return textToShow.toString()
}

@Preview
@Composable
private fun WifiConfigScreen_Preview() {
    AppTheme {
        WifiConfigScreen(
            viewModel = WifiConfigViewModel(
                EAPIdentityProviderList()
            ),
            closeApp = {}
        )
    }
}