package app.eduroam.geteduroam.profile

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.eduroam.geteduroam.R
import app.eduroam.geteduroam.Route
import app.eduroam.geteduroam.config.AndroidConfigParser
import app.eduroam.geteduroam.config.model.ClientSideCredential
import app.eduroam.geteduroam.config.model.EAPIdentityProviderList
import app.eduroam.geteduroam.config.model.bestMethod
import app.eduroam.geteduroam.config.requiresUsernamePrompt
import app.eduroam.geteduroam.di.api.GetEduroamApi
import app.eduroam.geteduroam.di.repository.StorageRepository
import app.eduroam.geteduroam.models.Profile
import app.eduroam.geteduroam.ui.ErrorData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class SelectProfileViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    val api: GetEduroamApi,
    private val repository: StorageRepository,
) : ViewModel() {

    private val parser = AndroidConfigParser()

    var uiState by mutableStateOf(UiState())
        private set
    private val organizationId: String
    private var didAgreeToTerms = false

    init {
        organizationId = savedStateHandle.get<String>(Route.SelectProfile.institutionIdArg) ?: ""
        if (organizationId.isNotBlank()) {
            loadData()
        } else {
            uiState = uiState.copy(
                inProgress = false, errorData = ErrorData(
                    titleId = R.string.err_title_generic_fail,
                    messageId = R.string.err_msg_invalid_organization_id
                )
            )
        }
    }

    private fun loadData() = viewModelScope.launch {
        uiState = uiState.copy(inProgress = true)
        val response = api.getOrganizations()
        val institutionResult = response.body()
        if (response.isSuccessful && institutionResult != null) {
            val selectedInstitution = institutionResult.instances.find { it.id == organizationId }
            if (selectedInstitution != null) {
                val isSingleProfile = selectedInstitution.profiles.size == 1
                val presentProfiles = selectedInstitution.profiles.mapIndexed { index, profile ->
                    PresentProfile(
                        profile = profile, isSelected = isSingleProfile || index == 0
                    )
                }
                uiState = uiState.copy(
                    inProgress = isSingleProfile,
                    profiles = presentProfiles,
                    organization = PresentOrganization(
                        name = selectedInstitution.name, location = selectedInstitution.country
                    )
                )
                if (isSingleProfile) {
                    Timber.i("Single profile for institution. Continue with configuration")
                    connectWithProfile(selectedInstitution.profiles[0], startOAuthFlowIfNoAccess = true)
                }
            } else {
                Timber.e("Could not find institution with id $organizationId")
                uiState = uiState.copy(
                    inProgress = false,
                    errorData = ErrorData(
                        titleId = R.string.err_title_generic_fail,
                        messageId = R.string.err_msg_cannot_find_organization,
                        messageArg = organizationId
                    )
                )
            }
        } else {
            val failReason = "${response.code()}/${response.message()}]${
                response.errorBody()?.string()
            }"
            Timber.e("Failed to load institutions: $failReason")
            uiState = uiState.copy(
                inProgress = false,
                errorData = ErrorData(
                    titleId = R.string.err_title_generic_fail,
                    messageId = R.string.err_msg_generic_unexpected_with_arg,
                    messageArg = failReason
                )
            )

        }
    }

    fun setProfileSelected(profile: PresentProfile) {
        uiState = uiState.copy(
            profiles = uiState.profiles.map {
                it.copy(isSelected = it.profile == profile.profile)
            }
        )
    }

    fun connectWithSelectedProfile() = viewModelScope.launch {
        val profile = uiState.profiles.first { it.isSelected }
        connectWithProfile(profile.profile, startOAuthFlowIfNoAccess = true)
    }

    private suspend fun connectWithProfile(
        profile: Profile,
        startOAuthFlowIfNoAccess: Boolean
    ) {
        uiState = uiState.copy(inProgress = true)
        if (profile.eapconfigEndpoint != null) {
            if (profile.oauth) {
                Timber.i("Selected profile requires authentication.")

                if (repository.isAuthenticatedForConfig(profile.createConfiguration())) {
                    Timber.i("Already authenticated for this profile, continue with existing credentials")
                    val authState = repository.authState.first()
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
        } else {
            Timber.e("Missing EAP endpoint in profile configuration. Cannot continue with selected profile.")
            uiState = uiState.copy(
                inProgress = false, errorData = ErrorData(
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
            Timber.e(ex, "Unable to fetch EAP config from remote service")
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
                    helpDesk = info?.helpdesk,
                    requiredSuffix = firstProvider.authenticationMethod?.bestMethod()?.clientSideCredential?.innerIdentitySuffix
                ),
                providerInfo = info
            )
            if (info?.termsOfUse != null && !didAgreeToTerms) {
                uiState = uiState.copy(inProgress = false, showTermsOfUseDialog = true)
            } else if (firstProvider.requiresUsernamePrompt()) {
                uiState = uiState.copy(inProgress = false, showUsernameDialog = true, credentialsEnteredForProviderList = providers)
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
        uiState = uiState.copy(errorData = null)
    }

    fun didCancelLogin() {
        uiState = uiState.copy(showUsernameDialog = false)
    }

    fun didEnterLoginDetails(username: String, password: String) {
        val profileList = uiState.credentialsEnteredForProviderList ?: throw RuntimeException("Profile list not found for entered credentials!")
        profileList.eapIdentityProvider?.forEach { idp ->
            idp.authenticationMethod?.forEach { authMethod ->
                authMethod.clientSideCredential?.apply {
                    this.userName = username
                    this.password = password
                }
            }
        }
        uiState = uiState.copy(credentialsEnteredForProviderList = null, goToConfigScreenWithProviderList = profileList)

    }

    /**
     * Call this when you start the OAuth flow, to avoid recalling it each time the screen is composed, and also to trigger the next check
     */
    fun setOAuthFlowStarted() {
        uiState = uiState.copy(promptForOAuth = false, checkProfileWhenResuming = true)
    }

    suspend fun checkIfCurrentProfileHasAccess() {
        val profile = if (uiState.profiles.size == 1) {
            uiState.profiles.first()
        } else {
            uiState.profiles.firstOrNull { it.isSelected }
        }
        if (profile == null) {
            Timber.e("Could not resume connection flow, selected profile not found!")
        } else {
            uiState = uiState.copy(checkProfileWhenResuming = false)
            connectWithProfile(profile.profile, startOAuthFlowIfNoAccess = false)
        }
    }
}
