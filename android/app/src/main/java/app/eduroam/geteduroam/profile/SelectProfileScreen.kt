package app.eduroam.geteduroam.profile

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.eduroam.geteduroam.EduTopAppBar
import app.eduroam.geteduroam.R
import app.eduroam.geteduroam.config.model.EAPIdentityProviderList
import app.eduroam.geteduroam.config.model.ProviderInfo
import app.eduroam.geteduroam.config.model.localizedMatch
import app.eduroam.geteduroam.models.Configuration
import app.eduroam.geteduroam.organizations.TermsOfUseDialog
import app.eduroam.geteduroam.models.Profile
import app.eduroam.geteduroam.models.ConfigSource
import app.eduroam.geteduroam.organizations.ConfiguredOrganization
import app.eduroam.geteduroam.ui.AlertDialogWithSingleButton
import app.eduroam.geteduroam.ui.ErrorData
import app.eduroam.geteduroam.ui.LinkifyText
import app.eduroam.geteduroam.ui.PrimaryButton
import app.eduroam.geteduroam.ui.theme.AppTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import java.text.DateFormat
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.Period
import java.time.ZoneOffset
import java.util.Date

@Composable
fun SelectProfileScreen(
    viewModel: SelectProfileViewModel,
    goToOAuth: (Configuration) -> Unit,
    goToConfigScreen: (ConfiguredOrganization, String?, EAPIdentityProviderList) -> Unit,
    goToPrevious: () -> Unit
) = EduTopAppBar(
    title = stringResource(id = R.string.profiles_header),
    onBackClicked = goToPrevious
) { paddingValues ->
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val uriHandler = LocalUriHandler.current
    val currentGotoOauth by rememberUpdatedState(newValue = goToOAuth)

    LaunchedEffect(viewModel, lifecycle) {
        snapshotFlow { viewModel.uiState }
            .filter { it.promptForOAuth }
            .flowWithLifecycle(lifecycle)
            .collect { state ->
                val profile = state.profiles.first { it.isSelected }.profile
                viewModel.setOAuthFlowStarted()
                currentGotoOauth(profile.createConfiguration())
            }
    }

    LaunchedEffect(viewModel, lifecycle) {
        snapshotFlow { viewModel.uiState }
            .filter { it.checkProfileWhenResuming }
            .flowWithLifecycle(lifecycle)
            .collect { _ ->
                viewModel.checkIfCurrentProfileHasAccess()
            }
    }

    LaunchedEffect(viewModel, lifecycle) {
        snapshotFlow { viewModel.uiState }.distinctUntilChanged()
            .filter { it.goToConfigScreenWithProviderList != null }.flowWithLifecycle(lifecycle).collect { state ->
                val providerList = state.goToConfigScreenWithProviderList!!
                val source = if (viewModel.customHost != null) {
                    ConfigSource.Url
                } else {
                    ConfigSource.Discovery
                }
                val selectedProfileId = viewModel.uiState.profiles.firstOrNull { it.isSelected }?.profile?.id
                goToConfigScreen(
                    ConfiguredOrganization(
                        source = source,
                        id = if (viewModel.customHost != null) viewModel.customHost.toString() else viewModel.institutionId!!,
                        name = viewModel.uiState.organization?.name,
                        country = viewModel.uiState.organization?.location
                    ),
                    selectedProfileId,
                    providerList
                )
                viewModel.didGoToConfigScreen()
            }
    }

    LaunchedEffect(viewModel, lifecycle) {
        snapshotFlow { viewModel.uiState }.distinctUntilChanged()
            .filter { it.openUrlInBrowser != null }.flowWithLifecycle(lifecycle).collect { state ->
                uriHandler.openUri(state.openUrlInBrowser!!)
                viewModel.didOpenBrowserForRedirect()
            }
    }

    SelectProfileContent(
        modifier = Modifier
            .padding(paddingValues)
            .fillMaxSize(),
        profiles = viewModel.uiState.profiles,
        institution = viewModel.uiState.organization,
        configuredOrganization = viewModel.uiState.configuredOrganization,
        providerInfo = viewModel.uiState.providerInfo,
        providerLogoBitmap = viewModel.providerLogoBitmap,
        inProgress = viewModel.uiState.inProgress,
        errorData = viewModel.uiState.errorData,
        showAlertForConfiguringDifferentProfile = viewModel.uiState.showAlertForConfiguringDifferentProfile,
        errorDataShown = viewModel::errorDataShown,
        resetConfigurationAndSelectProfile = viewModel::resetConfigurationAndSelectProfile,
        setProfileSelected = viewModel::setProfileSelected,
        connectWithSelectedProfile = viewModel::connectWithSelectedProfile,
        profileExpiryTimestampMs = viewModel.uiState.profileExpiryTimestampMs,
        requestReconfiguration = viewModel::requestReconfiguration
    )

    if (viewModel.uiState.showTermsOfUseDialog) {
        TermsOfUseDialog(
            providerInfo = viewModel.uiState.providerInfo,
            onConfirmClicked = {
                viewModel.didAgreeToTerms(true)
            }, onDismiss = {
                viewModel.didAgreeToTerms(false)
            }
        )
    }
}

@Composable
fun SelectProfileContent(
    modifier: Modifier = Modifier,
    profiles: List<PresentProfile>,
    institution: PresentOrganization?,
    configuredOrganization: ConfiguredOrganization?,
    profileExpiryTimestampMs: Long?,
    providerInfo: ProviderInfo?,
    providerLogoBitmap: android.graphics.Bitmap?,
    inProgress: Boolean,
    errorData: ErrorData?,
    showAlertForConfiguringDifferentProfile: PresentProfile?,
    errorDataShown: () -> Unit = {},
    resetConfigurationAndSelectProfile: (PresentProfile) -> Unit = {},
    setProfileSelected: (PresentProfile) -> Unit = {},
    connectWithSelectedProfile: () -> Unit = {},
    requestReconfiguration: () -> Unit = {}
) = Surface(
    modifier = modifier
) {
    val context = LocalContext.current
    errorData?.let {
        AlertDialogWithSingleButton(
            title = it.title(context),
            explanation = it.message(context),
            buttonLabel = stringResource(id = R.string.button_ok),
            onDismiss = errorDataShown
        )
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 24.dp)
    ) {
        val institutionName = institution?.name ?: configuredOrganization?.name
        institutionName?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth(),
                fontWeight = FontWeight.Bold
            )
        }
        val location = institution?.location ?: configuredOrganization?.country
        location?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.profiles_title),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(4.dp))
        HorizontalDivider(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
        )
        if (inProgress) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(4.dp))
        LazyColumn(Modifier.weight(1f)) {
            items(profiles) { profile ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .sizeIn(minHeight = 48.dp)
                        .clickable(onClick = {
                            setProfileSelected(profile)
                        }), verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = profile.profile.getLocalizedName(),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.fillMaxWidth(0.9f)
                    )
                    if (profile.isSelected) {
                        Icon(
                            imageVector = Icons.Outlined.Check,
                            contentDescription = "",
                            modifier = Modifier.size(ButtonDefaults.IconSize)
                        )
                    }
                }
                HorizontalDivider(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                )
            }
            if (providerInfo != null) {
                item {
                    Row(
                        modifier = Modifier
                            .padding(vertical = 16.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        providerLogoBitmap?.let { logoBitmap ->
                            Surface(
                                modifier = Modifier.size(104.dp),
                                color = Color.White,
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Image(
                                    modifier = Modifier.fillMaxSize(),
                                    bitmap = logoBitmap.asImageBitmap(),
                                    contentDescription = stringResource(id = R.string.content_description_provider_logo)
                                )
                            }
                            Spacer(modifier = Modifier.size(16.dp))
                        }
                        Column(
                            modifier = Modifier.fillMaxWidth(fraction = 1f)
                        ) {
                            providerInfo.displayNames?.localizedMatch()?.let { displayName ->
                                Text(
                                    text = displayName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.size(8.dp))
                            }
                            val contactDetails = listOfNotNull(
                                providerInfo.helpdesk?.webAddress,
                                providerInfo.helpdesk?.emailAddress,
                                providerInfo.helpdesk?.phone
                            )
                            if (contactDetails.isNotEmpty()) {
                                Text(
                                    text = stringResource(id = R.string.helpdesk_title),
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Spacer(modifier = Modifier.size(8.dp))
                                contactDetails.forEach {
                                    LinkifyText(
                                        text = it,
                                        color = MaterialTheme.colorScheme.secondary,
                                        linkColor = MaterialTheme.colorScheme.secondary,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Spacer(modifier = Modifier.size(4.dp))
                                }
                            }
                            providerInfo.termsOfUse?.localizedMatch()?.let { termsOfUse ->
                                Text(
                                    text = stringResource(id = R.string.terms_of_use_dialog_title),
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Spacer(modifier = Modifier.size(4.dp))
                                LinkifyText(
                                    text = termsOfUse,
                                    color = MaterialTheme.colorScheme.secondary,
                                    linkColor = MaterialTheme.colorScheme.secondary,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        }
        val isExpired: Boolean
        if (profileExpiryTimestampMs != null) {
            val configuration = LocalConfiguration.current
            Spacer(modifier = Modifier.size(16.dp))
            val nowDate = LocalDateTime.now()
            val expiryDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(profileExpiryTimestampMs), ZoneOffset.UTC)
            val expiryDate = Date(profileExpiryTimestampMs)
            if (nowDate.isBefore(expiryDateTime)) {
                isExpired = false
                val duration = formatDuration(date1 = nowDate, date2 = expiryDateTime)
                val dateFormatter = DateFormat.getDateInstance(DateFormat.LONG, configuration.locales[0])
                val dateString = dateFormatter.format(expiryDate)
                Text(
                    text = AnnotatedString.fromHtml(stringResource(id = R.string.profile_status_account_valid_in_future, dateString, duration)),
                    style = MaterialTheme.typography.labelLarge,
                )
            } else {
                isExpired = true
                val duration = formatDuration(date1 = expiryDateTime, date2 = nowDate)
                val dateFormatter = DateFormat.getDateInstance(DateFormat.LONG, configuration.locales[0])
                val dateString = dateFormatter.format(expiryDate)
                Text(
                    text = AnnotatedString.fromHtml(stringResource(id = R.string.profile_status_account_valid_in_past, dateString, duration)),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        } else {
            isExpired = false
        }

        Spacer(Modifier.height(16.dp))
        val configuredProfile = profiles.firstOrNull { it.isConfigured }
        if (configuredProfile != null) {
            Row(
                modifier = Modifier.navigationBarsPadding(),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painterResource(R.drawable.ic_saved_organization),
                    contentDescription = "Saved profile indicator",
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(id = R.string.profiles_profile_configured),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                )
                Spacer(Modifier.width(12.dp))
                IconButton(
                    modifier = Modifier.padding(4.dp)
                        .background(Color.White.copy(alpha = 0.3f), shape = MaterialTheme.shapes.small),
                    onClick ={
                    requestReconfiguration()
                }) {
                    Icon(
                        painterResource(R.drawable.ic_profile_renew),
                        contentDescription = "Reconfigure profile",
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        } else {
            PrimaryButton(
                text = stringResource(R.string.button_connect),
                enabled = !inProgress,
                onClick = { connectWithSelectedProfile() },
                modifier = Modifier
                    .navigationBarsPadding()
            )
        }
    }
    if (showAlertForConfiguringDifferentProfile != null) {
        val profileName = showAlertForConfiguringDifferentProfile.profile.getLocalizedName()
        AlertDialog(
            onDismissRequest = {
                errorDataShown()
            },
            title = {
                Text(
                    text = stringResource(R.string.profiles_reconfigure_profile, profileName),
                    style = MaterialTheme.typography.titleLarge,
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.profiles_do_you_want_to_reconfigure_device, profileName),
                    style = MaterialTheme.typography.bodyLarge,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        resetConfigurationAndSelectProfile(showAlertForConfiguringDifferentProfile)
                        connectWithSelectedProfile()
                        errorDataShown()
                    }
                ) {
                    Text(
                        text = stringResource(R.string.profiles_reconfigure_profile),
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        errorDataShown()
                    }
                ) {
                    Text(
                        text = stringResource(R.string.profiles_reconfigure_cancel),
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    )
                }
            })
    }

}

@Composable
fun formatDuration(date1: LocalDateTime, date2: LocalDateTime): String {
    val period = Period.between(date1.toLocalDate(), date2.toLocalDate())
    val duration = Duration.between(date1, date2)

    val days = period.days
    val months = period.months
    val years = period.years

    val hours = duration.toHours().toInt()
    val minutes = (duration.toMinutes() % 60).toInt()
    val seconds = (duration.seconds % 60).toInt()
    return if (years > 0) {
        "$years ${pluralStringResource(id = R.plurals.time_years, years)}"
    } else if (months > 0) {
        "$months ${pluralStringResource(id = R.plurals.time_months, months)}"
    } else if (days > 0) {
        "$days ${pluralStringResource(id = R.plurals.time_days, days)}"
    } else if (hours > 0) {
        "$hours ${pluralStringResource(id = R.plurals.time_hours, hours)}"
    } else if (minutes > 0) {
        "$minutes ${pluralStringResource(id = R.plurals.time_minutes, minutes)}"
    } else {
        "$seconds ${pluralStringResource(id = R.plurals.time_seconds, seconds)}"
    }
}


@Preview(uiMode = android.content.res.Configuration.UI_MODE_NIGHT_NO)
@Composable
private fun Preview_SelectProfileModal() {
    AppTheme {
        SelectProfileContent(
            profiles = profileList,
            institution = PresentOrganization("Uninett", "NO"),
            configuredOrganization = null,
            profileExpiryTimestampMs = LocalDateTime.now().plusDays(3).toEpochSecond(ZoneOffset.UTC) * 1000,
            providerInfo = null,
            providerLogoBitmap = null,
            inProgress = false,
            errorData = null,
            showAlertForConfiguringDifferentProfile = null
        )
    }
}

private val profileList = listOf(
    PresentProfile(Profile(id = "id", name = mapOf("any" to "First profile"), type = Profile.Type.eapConfig), true),
    PresentProfile(Profile(id = "id", name = mapOf("any" to "Second profile"), type = Profile.Type.eapConfig), false),
)