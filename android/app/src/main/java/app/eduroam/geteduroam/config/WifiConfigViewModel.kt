package app.eduroam.geteduroam.config

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSuggestion
import android.net.wifi.hotspot2.PasspointConfiguration
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.eduroam.geteduroam.NavTypes
import app.eduroam.geteduroam.Route
import app.eduroam.geteduroam.config.model.EAPIdentityProviderList
import app.eduroam.geteduroam.di.repository.NotificationRepository
import app.eduroam.geteduroam.di.repository.StorageRepository
import app.eduroam.geteduroam.models.ConfigSource
import app.eduroam.geteduroam.organizations.ConfiguredOrganization
import app.eduroam.geteduroam.ui.theme.IS_EDUROAM
import app.eduroam.geteduroam.ui.theme.isChromeOs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.lang.UnsupportedOperationException
import javax.inject.Inject

@HiltViewModel
class WifiConfigViewModel @Inject constructor(
    private val notificationRepository: NotificationRepository,
    private val storageRepository: StorageRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val eapIdentityProviderList: EAPIdentityProviderList
    val configuredOrganization: ConfiguredOrganization

    val launch: MutableStateFlow<Unit?> = MutableStateFlow(null)
    val progressMessage = MutableStateFlow("")
    val requestChangeNetworkPermission = MutableStateFlow(false)
    val processing = MutableStateFlow(true)
    val intentWithSuggestions: MutableStateFlow<Intent?> = MutableStateFlow(null)
    val showUsernameDialog = MutableStateFlow(false)
    val showPassphraseDialog = MutableStateFlow(false)
    val passphraseDialogRetryCount = MutableStateFlow(0)
    val didEnterUserCredentials = MutableStateFlow(false)
    val isRetryLaunch = MutableStateFlow(false)

    init {
        launch.value = Unit
        val data = savedStateHandle.toRoute<Route.ConfigureWifi>(NavTypes.allTypesMap)
        eapIdentityProviderList = data.eapIdentityProviderList
        configuredOrganization = data.configuredOrganization
    }

    fun launchConfiguration(context: Context, fallbackToSuggestions: Boolean = false) = viewModelScope.launch {
        launch.value = null
        isRetryLaunch.value = fallbackToSuggestions
        try {
            if (eapIdentityProviderList.eapIdentityProvider?.firstOrNull()?.requiresUsernamePrompt() == true && !didEnterUserCredentials.value) {
                showUsernameDialog.value = true
                return@launch
            }
        } catch (mpEx: MissingPassphraseException) {
            passphraseDialogRetryCount.value += 1
            showPassphraseDialog.value = true
            return@launch
        }
        passphraseDialogRetryCount.value = 0

        val intentsNotAllowed = Build.BRAND.startsWith("oneplus", ignoreCase = true) && Build.VERSION.SDK_INT == Build.VERSION_CODES.R // OnePlus devices running Android 11 (https://github.com/geteduroam/mobile-app/issues/113)
                // Feel free to add more cases here if needed

        when {
            //Android 11 and higher - API 30 - ChromeOS - we show everything in one intent
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && context.isChromeOs() -> {
                handleAndroid11ChromeOs()
            }

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && IS_EDUROAM && !fallbackToSuggestions && !intentsNotAllowed -> {
                // We will use Intent for SSID, and Suggestion for Passpoint.
                // It would've been possible to use Intent for both SSID and Passpoint,
                // but we can never remove intents for Passpoint.
                // Android will overwrite existing intents if it considers the new Passpoint configuration similar enough,
                // but it considers the certificate (among other things) in checking for equality, so "similar" is too high a bar.
                // For SSIDs it works well, it's considered equal if the SSID matches.
                // This is why we use intents for SSIDs and suggestions for Passpoint.

                // We don't use suggestions for SSIDs (if we can avoid it) because it would confuse users;
                // suggestions are prioritized under user-configured (e.g. has ever connected to) networks,
                // which includes any onboarding guest network, nearby Starbucks and lab-raspberry-pi setup.
                // Worse, when the user does notice this and explicitly wants to connect to eduroam,
                // the network appears unconfigured in the Wi-Fi picker.
                // This would confuse both users and help desks.
                handleAndroid11PhoneOrTablet(context)
            }
            //Android 10 - API 29
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                // Not using intent, only suggestions, since the API is not available
                if (hasPermission(context)) {
                    handleAndroid10WifiConfig(context)
                } else {
                    requestChangeNetworkPermission.value = true
                }
            }
            //All things Android 9 and lower - API 28
            else -> {
                handleAndroid9AndLower(context)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun removeNetworks(context: Context) {
        val wifiManager: WifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        // Empty list removes all networks
        wifiManager.removeNetworkSuggestions(emptyList<WifiNetworkSuggestion>())
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun removeNetworks(context: Context, vararg ssids: String) {
        val wifiManager: WifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        if (ssids.isNotEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val suggestions: MutableList<WifiNetworkSuggestion> = wifiManager.networkSuggestions
            for (suggestion in suggestions) {
                for (ssid in ssids) {
                    if (ssid == suggestion.ssid) break
                }
                suggestions.remove(suggestion)
            }
            wifiManager.removeNetworkSuggestions(suggestions)
        } else {
            removeNetworks(context)
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun handleAndroid11ChromeOs() {
        // We don't remove networks here, because networks added by an intent cannot be removed.
        val suggestions = eapIdentityProviderList.buildAllNetworkSuggestions()
        val intent = createSuggestionsIntent(suggestions = suggestions)
        intentWithSuggestions.value = intent
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun handleAndroid11PhoneOrTablet(context: Context) {
        val suggestions = eapIdentityProviderList.buildSSIDSuggestions()
        val intent = createSuggestionsIntent(suggestions = suggestions)
        intentWithSuggestions.value = intent
        val passPointSuggestion = eapIdentityProviderList.buildPasspointSuggestion()
        if (passPointSuggestion != null) {
            removeNetworks(context)
            val wifiManager: WifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            try {
                val status = wifiManager.addNetworkSuggestions(listOf(passPointSuggestion))
                if (status != 0) {
                    Timber.w("Status for adding network: $status")
                } else {
                    Timber.i("Successfully added network.")
                }
            } catch (e: Exception) {
                if (Build.VERSION.SDK_INT >= 30) {
                    // On Android 10 and lower we do not display Passpoint errors because the platform implementation is very unreliable
                    progressMessage.value = "Failed to add Passpoint suggestion. Exception: ${e.message}"
                }
                Timber.w(e, "Failed to add network suggestion")
            }
        }
        processing.value = false
    }

    private fun PasspointConfiguration.install(context: Context) {
        try {
            val wifiManager: WifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            try {
                // Remove any existing networks with the same FQDN
                @Suppress("DEPRECATION")
                wifiManager.removePasspointConfiguration(this.homeSp.fqdn)
            } catch (e: java.lang.IllegalArgumentException) {
                // According to the documentation, IllegalArgumentException can be thrown
                // But after testing, we see that SecurityException will be thrown
                // with message "Permission denied".

                // This error makes sense when observed (maybe we can't remove the network),
                // but it's undocumented that this error can be thrown.
            } catch (e: SecurityException) {
                // Ignore
            } catch (e: UnsupportedOperationException) {
                // Ignore
            }

            wifiManager.addOrUpdatePasspointConfiguration(this)
        } catch (e: IllegalArgumentException) {
            // Can throw when configuration is wrong or device does not support Passpoint
            // while we did encounter a few devices without Passpoint support.
            // On Android 10 and lower we do not display Passpoint errors because the platform implementation is very unreliable
            // - so there is no user visible message here.
            Timber.w(e, "Failed to add or update Passpoint config")
        } catch (e: UnsupportedOperationException) {
            // Ignore
        }
    }

    /**
     * Requires CHANGE_WIFI_STATE permission
     * */
    @RequiresApi(Build.VERSION_CODES.Q)
    fun handleAndroid10WifiConfig(context: Context) {
        val ssidSuggestions = eapIdentityProviderList.buildSSIDSuggestions()
        val wifiManager: WifiManager =
            context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val ssids = eapIdentityProviderList.eapIdentityProvider?.firstOrNull()?.credentialApplicability?.mapNotNull { it.ssid } ?: emptyList()
        removeNetworks(context, *ssids.toTypedArray())

        try {
            val status = wifiManager.addNetworkSuggestions(ssidSuggestions)
            if (status != 0) {
                Timber.w("Status for adding network: $status")
            } else {
                Timber.i("Successfully added network.")
            }
        } catch (e: Exception) {
            progressMessage.value = "Failed to add WiFi Suggestions. Exception: ${e.message}"
            Timber.w(e, "Failed to add network suggestion")
        }

        val passpointConfig = eapIdentityProviderList.buildPasspointConfig()
        try {
            passpointConfig?.install(context)
        } catch (e: IllegalArgumentException) {
            // Can throw when configuration is wrong or device does not support Passpoint
            // while we did encounter a few devices without Passpoint support.
            if (Build.VERSION.SDK_INT >= 30) {
                // On Android 10 and lower we do not display Passpoint errors because the platform implementation is very unreliable
                progressMessage.value = "Failed to add Passpoint. Exception: ${e.message}"
            }
            Timber.w(e, "Failed to add or update Passpoint config")

        }
        processing.value = false
    }

    @Suppress("DEPRECATION")
    private fun handleAndroid9AndLower(context: Context) {
        val wifiConfigs = eapIdentityProviderList.buildWifiConfigurations()
        val passpointConfig = eapIdentityProviderList.buildPasspointConfig()

        val wifiManager: WifiManager =
            context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

        wifiConfigs.forEach { wifiConfig ->
            try {
                val networkId = wifiManager.addNetwork(wifiConfig)
                val didAddNetwork = networkId != -1
                if (didAddNetwork) {
                    wifiManager.disconnect()
                    wifiManager.enableNetwork(networkId, true)
                    wifiManager.reconnect()
                    wifiManager.isWifiEnabled = true
                }
            } catch (e: Exception) {
                progressMessage.value =
                    "Failed to add/connect WifiConfiguration. Exception: ${e.message}"
                Timber.w(e, "Failed to add/connect WifiConfiguration")
            }
        }
        passpointConfig?.install(context)
        processing.value = false
    }

    private fun hasPermission(context: Context): Boolean = ContextCompat.checkSelfPermission(
        context, Manifest.permission.CHANGE_WIFI_STATE
    ) == PackageManager.PERMISSION_GRANTED

    @RequiresApi(Build.VERSION_CODES.R)
    private fun createSuggestionsIntent(suggestions: List<WifiNetworkSuggestion>?): Intent {
        val forBundle = ArrayList<WifiNetworkSuggestion>()
        if (suggestions != null) {
            forBundle.addAll(suggestions)
        }
        val bundle = Bundle().apply {
            putParcelableArrayList(Settings.EXTRA_WIFI_NETWORK_LIST, forBundle)
        }
        val intent = Intent(Settings.ACTION_WIFI_ADD_NETWORKS)
        return intent.apply {
            putExtras(bundle)
        }
    }

    fun consumeSuggestionIntent() {
        intentWithSuggestions.value = null
    }

    fun markAsComplete() {
        processing.value = false
    }

    fun didEnterPassphrase(passphrase: String) {
        eapIdentityProviderList.eapIdentityProvider?.forEach { idp ->
            idp.authenticationMethod?.forEach { authMethod ->
                authMethod.clientSideCredential?.passphrase = passphrase
            }
        }
        showPassphraseDialog.value = false
    }

    fun didEnterLoginDetails(username: String, password: String) {
        eapIdentityProviderList.eapIdentityProvider?.forEach { idp ->
            idp.authenticationMethod?.forEach { authMethod ->
                authMethod.clientSideCredential?.apply {
                    this.userName = username
                    this.password = password
                }
            }
        }
        showUsernameDialog.value = false
        didEnterUserCredentials.value = true
    }

    fun shouldRequestPushPermission(): Boolean {
        eapIdentityProviderList.eapIdentityProvider?.firstOrNull()?.let {
            return notificationRepository.shouldRequestPushPermission(it, configuredOrganization.id)
        }
        return false
    }

    fun scheduleReminderNotification() {
        eapIdentityProviderList.eapIdentityProvider?.firstOrNull()?.let {
            notificationRepository.scheduleNotificationIfNeeded(it, configuredOrganization.id)
        }
    }

    fun saveConfigForStatusScreen() {
        viewModelScope.launch {
            storageRepository.saveConfigForStatusScreen(
                configuredOrganization = configuredOrganization,
                expiryTimestampMs = eapIdentityProviderList.eapIdentityProvider?.firstOrNull()?.validUntil?.time,
                config = eapIdentityProviderList
            )
        }
    }

}