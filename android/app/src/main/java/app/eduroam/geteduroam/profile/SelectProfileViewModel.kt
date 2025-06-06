package app.eduroam.geteduroam.profile

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.eduroam.geteduroam.R
import app.eduroam.geteduroam.config.AndroidConfigParser
import app.eduroam.geteduroam.config.model.EAPIdentityProviderList
import app.eduroam.geteduroam.di.api.GetEduroamApi
import app.eduroam.geteduroam.di.repository.StorageRepository
import app.eduroam.geteduroam.models.DiscoveryResult
import app.eduroam.geteduroam.models.Profile
import app.eduroam.geteduroam.ui.ErrorData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class SelectProfileViewModel @Inject constructor(
    val api: GetEduroamApi,
    private val storageRepository: StorageRepository,
) : ViewModel() {

    private val parser = AndroidConfigParser()

    var uiState by mutableStateOf(UiState())
        private set

    var institutionId: String? = null
        private set
    var customHost: Uri? = null
        private set

    private var didAgreeToTerms = false
    private var previouslyConfiguredProfileId: String? = null

    fun setData(context: Context, institutionId: String?, customHostUri: String?) {
        customHost = customHostUri?.let { it.toUri() }
        this@SelectProfileViewModel.institutionId = institutionId
        if (institutionId?.isNotBlank() == true) {
            loadDataFromInstitution()
        } else if (customHost != null) {
            loadDataFromCustomHost(context)
        } else {
            uiState = uiState.copy(
                inProgress = false, errorData = ErrorData(
                    titleId = R.string.err_title_generic_fail,
                    messageId = R.string.err_msg_invalid_organization_id
                )
            )
        }
        viewModelScope.launch {
            val configuredOrganization = storageRepository.configuredOrganization.first()
            if (institutionId == configuredOrganization?.id) {
                previouslyConfiguredProfileId = storageRepository.configuredProfileLastConfig.firstOrNull()?.first
                val profileExpiryTimestampMs = storageRepository.profileExpiryTimestampMs.first()
                uiState = uiState.copy(
                    configuredOrganization = configuredOrganization,
                    profileExpiryTimestampMs = profileExpiryTimestampMs
                )
            }
        }
    }

    private fun loadDataFromInstitution() = viewModelScope.launch {
        uiState = uiState.copy(inProgress = true)
        var responseError: String? = null
        val institutionResult: DiscoveryResult? = try {
            val response = api.discover()
            if (!response.isSuccessful) {
                responseError = "${response.code()}/${response.message()}]${response.errorBody()?.string()}"
            }
            response.body()
        } catch (ex: Exception) {
            Timber.w(ex, "Could not fetch organizations!")
            if (responseError == null) {
                responseError = ex.message
            }
            null
        }
        if (institutionResult != null) {
            val selectedInstitution = institutionResult.content.institutions.find { it.id == institutionId }
            if (selectedInstitution != null) {
                val isSingleProfile = selectedInstitution.profiles.size == 1
                val profileIdToSelect  = if (previouslyConfiguredProfileId != null) {
                    previouslyConfiguredProfileId
                } else {
                    selectedInstitution.profiles.firstOrNull()?.id
                }
                val presentProfiles = selectedInstitution.profiles.map { profile ->
                    val result: PresentProfile
                    if (profile.type == Profile.Type.letswifi) {
                        try {
                            result = PresentProfile(
                                profile = resolveLetswifiProfile(profile),
                                isConfigured = previouslyConfiguredProfileId == profile.id,
                                isSelected = profileIdToSelect == profile.id
                            )
                        } catch (ex: Exception) {
                            Timber.w(ex, "Could not fetch letswifi profile!")
                            uiState = uiState.copy(
                                inProgress = false,
                                errorData = ErrorData(
                                    titleId = R.string.err_title_generic_fail,
                                    messageId = R.string.err_msg_could_not_discover_profile_configuration
                                )
                            )
                            return@launch
                        }
                    } else {
                        result = PresentProfile(
                            profile = profile,
                            isConfigured = previouslyConfiguredProfileId == profile.id,
                            isSelected = profileIdToSelect == profile.id
                        )
                    }
                    result
                }
                val autoConnectWithSingleProfile = isSingleProfile && !presentProfiles[0].isConfigured
                uiState = uiState.copy(
                    inProgress = autoConnectWithSingleProfile,
                    profiles = presentProfiles,
                    organization = PresentOrganization(
                        name = selectedInstitution.getLocalizedName(), location = selectedInstitution.country
                    )
                )
                if (autoConnectWithSingleProfile) {
                    Timber.i("Single profile for institution. Continue with configuration")
                    connectWithProfile(presentProfiles[0].profile, startOAuthFlowIfNoAccess = true)
                }
            } else {
                Timber.w("Could not find institution with id $institutionId")
                uiState = uiState.copy(
                    inProgress = false,
                    errorData = ErrorData(
                        titleId = R.string.err_title_generic_fail,
                        messageId = R.string.err_msg_cannot_find_organization,
                        messageArg = institutionId
                    )
                )
            }
        } else {
            Timber.w("Failed to load institutions: $responseError")
            uiState = uiState.copy(
                inProgress = false,
                errorData = ErrorData(
                    titleId = R.string.err_title_generic_fail,
                    messageId = R.string.err_msg_generic_unexpected_with_arg,
                    messageArg = responseError
                )
            )

        }
    }

    private fun loadDataFromCustomHost(context: Context) = viewModelScope.launch {
        uiState = uiState.copy(inProgress = true)
        try {
            val profile = resolveLetswifiProfile(Profile(
                id = customHost.toString(),
                name = mapOf("any" to context.getString(R.string.name)),
                type = Profile.Type.letswifi,
                letswifiEndpoint = customHost.toString()
            ))
            uiState = uiState.copy(
                inProgress = true,
                profiles = listOf(PresentProfile(profile = profile, isConfigured = false, isSelected = true)),
                organization = PresentOrganization(
                    name = customHost?.host, location = ""
                )
            )
            connectWithSelectedProfile()
        } catch (ex: Exception) {
            Timber.w(ex, "Could not fetch letswifi profile!")
            uiState = uiState.copy(
                inProgress = false,
                errorData = ErrorData(
                    titleId = R.string.err_title_generic_fail,
                    messageId = R.string.err_msg_could_not_discover_profile_configuration
                )
            )
            return@launch
        }

    }

    private suspend fun resolveLetswifiProfile(letswifiProfile: Profile): Profile {
        val endpoint = letswifiProfile.letswifiEndpoint
        if (endpoint.isNullOrEmpty()) {
            throw IllegalArgumentException("Missing letswifi endpoint!")
        }
        val response = api.getLetswifiConfig(endpoint).body()?.content ?: throw IllegalStateException("Could not fetch letswifi config!")
        return Profile(
            eapconfigEndpoint = response.eapConfigEndpoint,
            id = letswifiProfile.id,
            name = letswifiProfile.name,
            oauth = true,
            authorizationEndpoint = response.authorizationEndpoint,
            tokenEndpoint =  response.tokenEndpoint,
            type = Profile.Type.eapConfig
        )
    }

    fun setProfileSelected(profile: PresentProfile) {
        val hasConfiguredProfile = uiState.profiles.any { it.isConfigured }
        if (!profile.isSelected && hasConfiguredProfile && !profile.isConfigured) {
            // We have a configured profile, and the user selected a different one
            uiState = uiState.copy(showAlertForConfiguringDifferentProfile = profile)
            return
        }
        uiState = uiState.copy(
            profiles = uiState.profiles.map {
                it.copy(isSelected = it.profile == profile.profile)
            }
        )
    }

    fun connectWithSelectedProfile() = viewModelScope.launch {
        val profile = uiState.profiles.firstOrNull { it.isSelected } ?: return@launch
        connectWithProfile(profile.profile, startOAuthFlowIfNoAccess = true)
    }

    private suspend fun connectWithProfile(
        profile: Profile,
        startOAuthFlowIfNoAccess: Boolean
    ) {
        Timber.i("Connecting with profile ${profile.name} (ID: ${profile.id})")
        uiState = uiState.copy(inProgress = true)
        if (profile.eapconfigEndpoint != null) {
            if (profile.oauth) {
                Timber.i("Selected profile requires authentication.")

                if (storageRepository.isAuthenticatedForConfig(profile.createConfiguration())) {
                    Timber.i("Already authenticated for this profile, continue with existing credentials")
                    val authState = storageRepository.authState.first()
                    viewModelScope.launch(Dispatchers.IO) {
                        getEapFrom(profile.eapconfigEndpoint, authState?.accessToken?.let { "Bearer $it" })
                    }
                } else if (startOAuthFlowIfNoAccess) {
                    Timber.i("Prompt for authentication for selected profile.")
                    uiState = uiState.copy(
                        promptForOAuth = true,
                    )
                } else {
                    uiState = uiState.copy(
                        errorData = ErrorData(
                            titleId = R.string.err_title_auth_failed,
                            messageId = R.string.err_msg_auth_token_failed
                        )
                    )
                }
            } else {
                Timber.i("Profile does not require OAuth")
                viewModelScope.launch(Dispatchers.IO) {
                    getEapFrom(profile.eapconfigEndpoint, null)
                }
            }
        } else if (!profile.redirect.isNullOrEmpty()) {
            uiState = uiState.copy(
                inProgress = false,
                openUrlInBrowser = profile.redirect
            )
        } else if (profile.type == Profile.Type.webview && !profile.webviewEndpoint.isNullOrEmpty()) {
            uiState = uiState.copy(
                inProgress = false,
                openUrlInBrowser = profile.webviewEndpoint
            )
        } else {
            Timber.w("Missing EAP endpoint in profile configuration. Cannot continue with selected profile.")
            uiState = uiState.copy(
                inProgress = false,
                errorData = ErrorData(
                    titleId = R.string.err_title_generic_fail,
                    messageId = R.string.err_msg_missing_eap_endpoint
                )
            )
        }
    }

    private suspend fun downloadEapConfig(url: String, authorizationHeader: String?): EAPIdentityProviderList? {
        val client = OkHttpClient.Builder().build()
        var requestBuilder = Request.Builder()
            .url(url)
            .method("POST", byteArrayOf().toRequestBody())
        if (authorizationHeader != null) {
            requestBuilder = requestBuilder.addHeader("Authorization", authorizationHeader)
        }
        try {
            val response = client.newCall(requestBuilder.build()).execute()
            val bytes = response.body?.bytes()
            response.close()
            if (bytes == null) {
                return null
            }
            return parser.parse(bytes)
        } catch (ex: Exception) {
            Timber.w(ex, "Unable to fetch EAP config from remote service")
            return null
        }
    }

    private suspend fun getEapFrom(eapEndpoint: String, authorizationHeader: String? = null) {
        val providers = downloadEapConfig(eapEndpoint, authorizationHeader)
        if (providers == null) {
            displayEapError()
            return
        }
        val firstProvider = providers.eapIdentityProvider?.firstOrNull()
        if (firstProvider != null) {
            val knownInstitution = uiState.organization
            val info = firstProvider.providerInfo
            uiState = uiState.copy(
                inProgress = true,
                organization = PresentOrganization(
                    name = knownInstitution?.name,
                    location = knownInstitution?.location,
                    displayName = info?.displayName,
                    description = info?.description,
                    logo = info?.providerLogo?.value,
                    termsOfUse = info?.termsOfUse,
                    helpDesk = info?.helpdesk
                ),
                providerInfo = info
            )
            if (info?.termsOfUse != null && !didAgreeToTerms) {
                uiState = uiState.copy(inProgress = false, showTermsOfUseDialog = true)
            } else {
                uiState = uiState.copy(inProgress = false, goToConfigScreenWithProviderList = providers)
            }
        } else {
            displayEapError()
        }
    }

    fun didAgreeToTerms(agreed: Boolean) {
        uiState = uiState.copy(showTermsOfUseDialog = false)
        didAgreeToTerms = agreed
        if (agreed) {
            connectWithSelectedProfile()
        }
    }

    private fun displayEapError() {
        uiState = uiState.copy(
            inProgress = false,
            errorData = ErrorData(
                titleId = R.string.err_title_generic_fail,
                messageId = R.string.err_msg_no_valid_provider
            )
        )
    }

    fun errorDataShown() {
        uiState = uiState.copy(errorData = null, showAlertForConfiguringDifferentProfile = null)
    }

    /**
     * Call this when you start the OAuth flow, to avoid recalling it each time the screen is composed, and also to trigger the next check
     */
    fun setOAuthFlowStarted() {
        uiState = uiState.copy(promptForOAuth = false, checkProfileWhenResuming = true)
    }

    fun didOpenBrowserForRedirect() {
        uiState = uiState.copy(openUrlInBrowser = null)
    }


    suspend fun checkIfCurrentProfileHasAccess() {
        val profile = if (uiState.profiles.size == 1) {
            uiState.profiles.first()
        } else {
            uiState.profiles.firstOrNull { it.isSelected }
        }
        if (profile == null) {
            Timber.w("Could not resume connection flow, selected profile not found!")
        } else {
            uiState = uiState.copy(checkProfileWhenResuming = false)
            connectWithProfile(profile.profile, startOAuthFlowIfNoAccess = false)
        }
    }

    fun didGoToConfigScreen() {
        uiState = uiState.copy(goToConfigScreenWithProviderList = null)
    }

    fun resetConfigurationAndSelectProfile(presentProfile: PresentProfile) {
        previouslyConfiguredProfileId = null
        uiState = uiState.copy(
            profiles = uiState.profiles.map { it.copy(isConfigured = false, isSelected = presentProfile == it) }
        )
    }

    fun requestReconfiguration() {
        uiState = uiState.copy(showAlertForConfiguringDifferentProfile = uiState.profiles.firstOrNull { it.isConfigured })
    }
}
