package app.eduroam.geteduroam.oauth

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.net.Uri
import android.util.Base64
import androidx.browser.customtabs.CustomTabsIntent
import androidx.browser.customtabs.CustomTabsService.ACTION_CUSTOM_TABS_CONNECTION
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.eduroam.geteduroam.R
import app.eduroam.geteduroam.di.assist.AuthenticationAssistant
import app.eduroam.geteduroam.di.repository.StorageRepository
import app.eduroam.geteduroam.models.Configuration
import app.eduroam.geteduroam.ui.ErrorData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ClientAuthentication
import net.openid.appauth.ClientSecretBasic
import net.openid.appauth.RegistrationRequest
import net.openid.appauth.ResponseTypeValues
import net.openid.appauth.TokenResponse
import timber.log.Timber
import java.security.MessageDigest
import java.security.SecureRandom
import javax.inject.Inject


@HiltViewModel
class OAuthViewModel @Inject constructor(
    private val repository: StorageRepository,
    private val assistant: AuthenticationAssistant,
) : ViewModel() {
    var uiState by mutableStateOf(UiState(OAuthStep.Loading))
        private set
    private val usePKCE = true
    private var service: AuthorizationService? = null
    private var configuration: Configuration = Configuration.EMPTY

    fun prepareAppAuth(
        configuration: Configuration,
        context: Context,
        redirectUri: Uri?) = viewModelScope.launch {
        this@OAuthViewModel.configuration = configuration
        uiState = UiState(OAuthStep.Loading)
        try {
            checkIfConfigurationChanged()
            initializeAppAuth(context, redirectUri != null)
            if (redirectUri != null) {
                val authRequest = repository.authRequest.first()
                uiState = if (authRequest != null) {
                    UiState(OAuthStep.GetTokensFromRedirectUri(redirectUri, authRequest))
                } else {
                    UiState(
                        OAuthStep.Error, ErrorData(
                            titleId = R.string.err_title_auth_unexpected_fail,
                            messageId = R.string.err_msg_auth_init_fail_arg,
                            messageArg = context.getString(R.string.err_msg_auth_no_matching_auth_request)
                        )
                    )
                }
                return@launch
            }
            val authorizationIntent = createAuthorizationIntent(context)
            if (authorizationIntent != null) {
                uiState = UiState(OAuthStep.Initialized(authorizationIntent))
            } else {
                uiState = UiState(OAuthStep.WebViewFallback(configuration, repository.authRequest.first()!!.toUri()))
            }
        } catch (e: Exception) {
            Timber.w(e, "Unable to initialize AppAuth!")
            if (e is ActivityNotFoundException) {
                // Could not find a browser good enough to open, we continue with WebView fallback
                try {
                    uiState = UiState(OAuthStep.WebViewFallback(configuration, repository.authRequest.first()!!.toUri()))
                    return@launch
                } catch (ex: Exception) {
                    Timber.e(ex,"Could not launch WebView fallback!")
                    uiState = UiState(
                        OAuthStep.Error, ErrorData(
                            titleId = R.string.err_title_auth_unexpected_fail,
                            messageId =  R.string.err_msg_auth_init_fail_arg,
                            messageArg = ex.toString()
                        )
                    )
                    return@launch
                }
            }
            val argument = e.message ?: e.localizedMessage
            uiState = UiState(
                OAuthStep.Error, ErrorData(
                    titleId = R.string.err_title_auth_unexpected_fail,
                    messageId = if (argument == null) R.string.err_msg_auth_init_fail else R.string.err_msg_auth_init_fail_arg,
                    messageArg = argument
                )
            )
            Timber.w(e, "Failed to prepare AppAuth")
        }
    }

    private fun isCustomTabSupported(context: Context, url: Uri?): Boolean {
        return getCustomTabsPackages(context, url).isNotEmpty()
    }

    /**
     * Returns a list of packages that support Custom Tabs.
     */
    private fun getCustomTabsPackages(context: Context, url: Uri?): List<ResolveInfo> {
        val pm = context.packageManager
        val activityIntent = Intent(Intent.ACTION_VIEW, url)
        val resolvedActivityList = pm.queryIntentActivities(activityIntent, 0)
        val packagesSupportingCustomTabs = mutableListOf<ResolveInfo>()
        for (info in resolvedActivityList) {
            val serviceIntent = Intent()
            serviceIntent.action = ACTION_CUSTOM_TABS_CONNECTION
            serviceIntent.setPackage(info.activityInfo.packageName)
            // Check if this package also resolves the Custom Tabs service.
            if (pm.resolveService(serviceIntent, 0) != null) {
                packagesSupportingCustomTabs.add(info)
            }
        }
        return packagesSupportingCustomTabs
    }


    private suspend fun createAuthorizationIntent(context: Context): Intent? {
        val currentAuthRequest = repository.authRequest.first()
            ?: throw IllegalStateException("AuthorizationRequest not available when trying to create authorization request Intent")
        val availableService = service
            ?: throw IllegalStateException("AuthorizationService not available when trying to create authorization request Intent")

        val requestUri = currentAuthRequest.toUri()
        if (isCustomTabSupported(context, requestUri)) {
            val customTabIntent = warmupBrowser()
            return availableService.getAuthorizationRequestIntent(currentAuthRequest, customTabIntent)
        } else {
            return null
        }
    }

    fun continueWithFetchToken(intent: Intent?) = viewModelScope.launch {
        Timber.d("Continue with fetch token")
        val currentAuthState = repository.authState.first()
        when {
            intent == null -> {
                uiState = UiState(
                    OAuthStep.Error, ErrorData(
                        titleId = R.string.err_title_auth_invalid,
                        messageId = R.string.err_msg_auth_invalid,
                    )
                )
            }

            currentAuthState == null -> {
                uiState = UiState(
                    OAuthStep.Error, ErrorData(
                        titleId = R.string.err_title_auth_failed,
                        messageId = R.string.err_msg_auth_failed,
                    )
                )
            }

            currentAuthState.isAuthorized && !currentAuthState.needsTokenRefresh -> {
                Timber.d("Current state is already authorized.")
                uiState = UiState(
                    oauthStep = OAuthStep.Authorized,
                    error = null,
                )
            }

            else -> {
                val response = AuthorizationResponse.fromIntent(intent)
                val ex = AuthorizationException.fromIntent(intent)
                Timber.d("Processing authorization response from intent.")
                if (response != null || ex != null) {
                    currentAuthState.update(response, ex)
                    repository.saveCurrentAuthState(currentAuthState)
                }
                if (response?.authorizationCode != null) {
                    try {
                        uiState = UiState(OAuthStep.ExchangingTokenRequest)
                        val tokenResponse = exchangeAuthorizationCode(response)
                        currentAuthState.update(tokenResponse, ex)
                        repository.saveCurrentAuthState(currentAuthState)
                        if (currentAuthState.isAuthorized) {
                            uiState = UiState(
                                oauthStep = OAuthStep.Authorized,
                                error = null,
                            )
                        } else {
                            uiState = UiState(
                                OAuthStep.Error, ErrorData(
                                    titleId = R.string.err_title_auth_failed,
                                    messageId = R.string.err_msg_auth_token_failed,
                                )
                            )
                        }
                    } catch (e: Exception) {
                        Timber.w(e, "Failed to exchange authorization code.")
                        val arg = "${e.javaClass.simpleName}: ${e.message}"
                        uiState = UiState(
                            OAuthStep.Error, ErrorData(
                                titleId = R.string.err_title_auth_failed,
                                messageId = R.string.err_msg_auth_code_failed_arg,
                                messageArg = arg
                            )
                        )
                    }
                } else if (ex != null) {
                    val arg = "${ex.javaClass.simpleName}: ${ex.message}"
                    uiState = UiState(
                        OAuthStep.Error, ErrorData(
                            titleId = R.string.err_title_auth_failed,
                            messageId = R.string.err_msg_generic_unexpected_with_arg,
                            messageArg = arg
                        )
                    )
                } else {
                    uiState = UiState(
                        OAuthStep.Error, ErrorData(
                            titleId = R.string.err_title_auth_failed,
                            messageId = R.string.err_msg_auth_failed,
                        )
                    )
                }
            }
        }
    }

    private suspend fun checkIfConfigurationChanged() {
        val lastKnownHash = repository.lastKnownConfigHash.first()
        if (configuration.hashCode() != lastKnownHash) {
            Timber.d("Configuration change detected, discarding old state")
            repository.saveCurrentAuthState(AuthState())
            repository.acceptNewConfiguration(configuration.hashCode())
        }
    }

    private suspend fun exchangeAuthorizationCode(response: AuthorizationResponse): TokenResponse {
        val currentAuthState = repository.authState.first()
            ?: throw IllegalStateException("AuthState not available when trying to exchange authorization code.")
        val availableService = service
            ?: throw IllegalStateException("AuthenticationService not available when trying to exchange authorization code.")

        val clientAuthentication = try {
            currentAuthState.clientAuthentication
        } catch (e: ClientAuthentication.UnsupportedAuthenticationMethod) {
            throw IllegalStateException("AuthenticationService not available when trying to exchange authorization code.")
        }
        return assistant.exchangeAuthorizationCode(
            response, clientAuthentication, availableService
        )
    }

    fun dismissError() {
        uiState = uiState.copy(error = null)
    }

    private suspend fun recreateAuthorizationService(context: Context) {
        service?.dispose()
        service = AuthenticationAssistant.createAuthorizationService(context)
        repository.saveCurrentAuthRequest(null)
    }

    private suspend fun initializeAppAuth(context: Context, hasRedirectUri: Boolean) {
        Timber.d("Initializing AppAuth")
        recreateAuthorizationService(context)
        if (hasRedirectUri) {
            return
        }

        val currentAuthState = repository.authState.first()
        if (currentAuthState?.authorizationServiceConfiguration != null) {
            // configuration is already created, skip to client initialization
            Timber.d("authorization service configuration already established")
            initializeClient()
            return
        }
        // if we are not using discovery, build the authorization service configuration directly
        // from the static configuration values.
        if (configuration.discoveryUri != null) {
            Timber.d("Creating AuthorizationServiceConfiguration from statically known configuration")
            val serviceConfig = AuthorizationServiceConfiguration(
                configuration.authEndpointUri,
                configuration.tokenEndpointUri,
                configuration.registrationEndpointUri,
                configuration.endSessionEndpointUri
            )
            repository.saveCurrentAuthState(AuthState(serviceConfig))
            initializeClient()
            return
        }
        val serviceConfig = assistant.retrieveOpenIdDiscoveryDoc(configuration)
        repository.saveCurrentAuthState(AuthState(serviceConfig))
        initializeClient()
    }

    private suspend fun initializeClient() {
        val staticClientId = configuration.clientId
        if (staticClientId != null) {
            Timber.d("Using static client id: $staticClientId")
            repository.saveClientId(staticClientId)
            createAuthRequest()
            return
        }
        val currentAuthState = repository.authState.first() ?: return
        val lastRegistrationResponse = currentAuthState.lastRegistrationResponse
        if (lastRegistrationResponse != null) {
            Timber.d("Using dynamic client id learned from previous registration: ${lastRegistrationResponse.clientId}")
            repository.saveClientId(lastRegistrationResponse.clientId)
            createAuthRequest()
            return
        }
        Timber.d("Dynamically registering client")
        val serviceConfiguration = currentAuthState.authorizationServiceConfiguration ?: return
        val registrationRequest = RegistrationRequest.Builder(
            serviceConfiguration, listOf(configuration.redirectUri)
        ).setTokenEndpointAuthenticationMethod(ClientSecretBasic.NAME).build()
        if (service != null) {
            val registrationResponse =
                assistant.performRegistrationRequest(registrationRequest, service!!)
            repository.saveClientId(registrationResponse.clientId)
            createAuthRequest()
        }
    }

    private suspend fun warmupBrowser(): CustomTabsIntent {
        Timber.d("Warming up browser instance for auth request. Building custom tab intent")
        val currentAuthRequest = repository.authRequest.first()
            ?: throw IllegalStateException("AuthorizationRequest not available when trying to create CustomTabsIntent")
        val availableService = service
            ?: throw IllegalStateException("AuthorizationService not available when trying to create CustomTabsIntent")
        val intentBuilder =
            availableService.createCustomTabsIntentBuilder(currentAuthRequest.toUri())
        return intentBuilder.build()
    }

    private suspend fun createAuthRequest(loginHint: String? = null) {
        if (usePKCE) {
            createAuthRequestViaPKCE(loginHint)
        } else {
            createAuthRequestViaAuthenticationFlow(loginHint)
        }
    }

    private suspend fun createAuthRequestViaAuthenticationFlow(loginHint: String? = null) {
        val currentAuthState = repository.authState.first()
            ?: throw IllegalStateException("AuthState not available when trying to create AuthorizationRequest")
        val currentClientId = repository.clientId.first()
            ?: throw IllegalStateException("clientId not available when trying to create AuthorizationRequest")
        val currentConfiguration = currentAuthState.authorizationServiceConfiguration
            ?: throw IllegalStateException("AuthorizationServiceConfiguration not available when trying to create AuthorizationRequest")
        val authRequestBuilder = AuthorizationRequest.Builder(
            currentConfiguration,
            currentClientId,
            ResponseTypeValues.CODE,
            configuration.redirectUri
        ).setScopes(configuration.scope, "openid", "profile", "email")
        if (loginHint?.isEmpty() == false) {
            authRequestBuilder.setLoginHint(loginHint)
        }
        repository.saveCurrentAuthRequest(authRequestBuilder.build())
    }

    private suspend fun createAuthRequestViaPKCE(loginHint: String? = null) {
        val secureRandom = SecureRandom()
        val bytes = ByteArray(64)
        secureRandom.nextBytes(bytes)

        val encoding = Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
        val codeVerifier = Base64.encodeToString(bytes, encoding)
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(codeVerifier.toByteArray())
        val codeChallenge = Base64.encodeToString(hash, encoding)

        val currentAuthState = repository.authState.first()
            ?: throw IllegalStateException("AuthState not available when trying to create AuthorizationRequest")
        val currentClientId = repository.clientId.first()
            ?: throw IllegalStateException("clientId not available when trying to create AuthorizationRequest")
        val currentConfiguration = currentAuthState.authorizationServiceConfiguration
            ?: throw IllegalStateException("AuthorizationServiceConfiguration not available when trying to create AuthorizationRequest")
        val authRequestBuilder = AuthorizationRequest.Builder(
            currentConfiguration,
            currentClientId,
            ResponseTypeValues.CODE,
            configuration.redirectUri
        ).setCodeVerifier(
            codeVerifier, codeChallenge, "S256"
        ).setScope(configuration.scope)
        repository.saveCurrentAuthRequest(authRequestBuilder.build())
    }

    override fun onCleared() {
        service?.dispose()
    }

    fun didGoToNextScreen() {
        uiState = uiState.copy(oauthStep = OAuthStep.Launched)
    }
}