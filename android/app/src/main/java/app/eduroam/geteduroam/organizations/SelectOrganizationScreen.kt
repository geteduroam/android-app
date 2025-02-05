package app.eduroam.geteduroam.organizations

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.flowWithLifecycle
import app.eduroam.geteduroam.R
import app.eduroam.geteduroam.config.model.EAPIdentityProviderList
import app.eduroam.geteduroam.models.Configuration
import app.eduroam.geteduroam.models.Organization
import app.eduroam.geteduroam.models.ConfigSource
import app.eduroam.geteduroam.models.Organization.Companion.LANGUAGE_KEY_FALLBACK
import app.eduroam.geteduroam.ui.ErrorData
import app.eduroam.geteduroam.ui.theme.AppTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

@Composable
fun SelectOrganizationScreen(
    viewModel: SelectOrganizationViewModel,
    openProfileModal: (String) -> Unit,
    goToOAuth: (Configuration) -> Unit,
    goToConfigScreen: (ConfiguredOrganization, String?, EAPIdentityProviderList) -> Unit,
    openFileUri: (Uri) -> Unit,
    discoverUrl: (Uri) -> Unit
) {
    val step: Step by remember { mutableStateOf(Step.Start) }
    var waitForVmEvent by rememberSaveable { mutableStateOf(false) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    LaunchedEffect(step) {
        when (val currentStep = step) {
            is Step.DoOAuthFor -> {
                viewModel.onStepCompleted()
                goToOAuth(currentStep.configuration)
            }

            is Step.DoConfig -> {
                viewModel.onStepCompleted()
                goToConfigScreen(
                    currentStep.configuredOrganization,
                    currentStep.configuredProfileId,
                    currentStep.eapIdentityProviderList)
            }

            is Step.PickProfileFrom -> {
                viewModel.onStepCompleted()
            }

            Step.Start -> {
                //Do nothing
            }
        }
    }
    LaunchedEffect(viewModel.uiState.configuredOrganization) {
        val organizationId = viewModel.uiState.configuredOrganization?.id
        if (organizationId != null && !viewModel.uiState.didShowConfiguredOrganization) {
            viewModel.configuredProfileModalShown()
            openProfileModal(organizationId)
        }
    }
    if (waitForVmEvent) {
        val currentOpenProfileModal by rememberUpdatedState(newValue = openProfileModal)
        LaunchedEffect(viewModel, lifecycle) {
            snapshotFlow { viewModel.uiState }.distinctUntilChanged()
                .filter { it.selectedOrganization != null }.flowWithLifecycle(lifecycle).collect {
                    waitForVmEvent = false
                    currentOpenProfileModal(
                        it.selectedOrganization?.id.orEmpty(),
                    )
                    viewModel.clearSelection()
                }
        }
    }

    SelectOrganizationContent(
        organizations = viewModel.uiState.organizations,
        configuredOrganization = viewModel.uiState.configuredOrganization,
        isLoading = viewModel.uiState.isLoading,
        onSelectOrganization = { organization ->
            waitForVmEvent = true
            viewModel.onOrganizationSelect(organization)
        },
        searchText = viewModel.uiState.filter,
        onSearchTextChange = { viewModel.onSearchTextChange(it) },
        onClearDialog = viewModel::clearDialog,
        onCredsAvailable = { username, password ->
            viewModel.creds.value = Pair(username, password)
        },
        errorData = viewModel.uiState.errorData,
        openFileUri = openFileUri,
        showConnectCta = viewModel.uiState.showConnectCta,
        discoverUrl = discoverUrl
    )
}


@Composable
fun SelectOrganizationContent(
    organizations: List<Organization> = emptyList(),
    configuredOrganization: ConfiguredOrganization? = null,
    isLoading: Boolean = false,
    showDialog: Boolean = false,
    errorData: ErrorData? = null,
    showConnectCta: Boolean = false,
    onSelectOrganization: (Organization) -> Unit,
    searchText: String,
    onSearchTextChange: (String) -> Unit = {},
    onClearDialog: () -> Unit = {},
    onCredsAvailable: (String, String) -> Unit = { _, _ -> },
    openFileUri: (Uri) -> Unit = {},
    discoverUrl: (Uri) -> Unit = {}
) = Surface(color = MaterialTheme.colorScheme.surface) {
    val context = LocalContext.current
    var showExtraActionsPopup by remember { mutableStateOf(false) }
    var popupPosition by remember { mutableStateOf(IntOffset(0, 0)) }
    val pickEapConfigFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { fileUri ->
        if (fileUri != null) {
            openFileUri(fileUri)
        }
    }
    if (showDialog) {
        LoginDialog({ username, password ->
            onCredsAvailable(username, password)
            onClearDialog()
        }, {})
    } else {
        // Center heart icon
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.fillMaxHeight(fraction = 0.3f))
            Icon(
                painterResource(R.drawable.ic_home_center),
                contentDescription = "App logo",
                tint = Color(0xFFBDD6E5),
                modifier = Modifier.size(150.dp)
            )
        }
        // Bottom right eduroam icon
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.End
        ) {
            Spacer(Modifier.fillMaxHeight(fraction = 0.8f))

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(topStartPercent = 50, bottomStartPercent = 50))
                    .background(Color.White)
                    .padding(horizontal = 32.dp, vertical = 16.dp)
            ) {
                Image(
                    painterResource(R.drawable.ic_home_bottom_right),
                    contentDescription = "App logo",
                    modifier = Modifier.width(120.dp)
                )
            }
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            OrganizationSearchHeader(
                searchText = searchText,
                onSearchTextChange = onSearchTextChange,
                onPositionDetermined = { position ->
                    popupPosition = position
                },
                showExtraActionsPopup = {
                    showExtraActionsPopup = true
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )
            if (isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    trackColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f)
                )
            }
            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.weight(1f)) {
                if (errorData != null) {
                    item {
                        Column {
                            Text(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                text = errorData.title(context),
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Spacer(Modifier.height(24.dp))
                        }
                    }
                } else {
                    if (showConnectCta) {
                        item {
                            TextButton(
                                onClick = {
                                    var uri = Uri.parse(searchText)
                                    if (uri.scheme == null) {
                                        uri = Uri.parse("https://$searchText")
                                    }
                                    discoverUrl(uri)
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        modifier = Modifier.weight(1f),
                                        color = MaterialTheme.colorScheme.secondary,
                                        text = stringResource(id = R.string.connect_to_server, searchText),
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Default.ArrowForward,
                                        contentDescription = "",
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(30.dp)
                                    )
                                }
                            }
                        }

                    }
                    if (!isLoading) {
                        if (organizations.isEmpty() && searchText.isNotEmpty() && !showConnectCta) {
                            item {
                                Text(
                                    modifier = Modifier.padding(16.dp),
                                    color = MaterialTheme.colorScheme.secondary,
                                    text = stringResource(id = R.string.organizations_no_results),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        } else {
                            items(organizations) { organization ->
                                val isSaved = organization.id == configuredOrganization?.id
                                OrganizationRow(organization, isSaved, onSelectOrganization)
                            }
                        }
                    }
                }
                if (searchText.isEmpty() && configuredOrganization != null) {
                    item {
                        OrganizationRow(
                            Organization(
                                country = configuredOrganization.country ?: "",
                                id = configuredOrganization.id,
                                name = mapOf(LANGUAGE_KEY_FALLBACK to (configuredOrganization.name ?: configuredOrganization.id)),
                                profiles = emptyList()
                            ),
                            isSavedOrganization = true,
                            onSelectOrganization
                        )
                    }
                }
            }
        }
    }
    if (showExtraActionsPopup) {
        Popup(
            offset = popupPosition,
            onDismissRequest = {
                showExtraActionsPopup = false
            }
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    TextButton(
                        onClick = {
                            showExtraActionsPopup = false
                            // Open file chooser
                            pickEapConfigFileLauncher.launch("*/*") // It would be nice to filter on .eap-config files, but it is currently not possible
                        }
                    ) {
                        Text(text = stringResource(id = R.string.open_eap_config_file))
                    }
                }
            }
        }
    }
}


@Preview
@Composable
fun Preview_SelectOrganizationContent() {
    AppTheme {
        SelectOrganizationContent(
            onSelectOrganization = {},
            searchText = ""
        )
    }
}