package app.eduroam.geteduroam.profile

import android.net.Uri
import app.eduroam.geteduroam.config.model.EAPIdentityProviderList
import app.eduroam.geteduroam.config.model.Helpdesk
import app.eduroam.geteduroam.config.model.ProviderInfo
import app.eduroam.geteduroam.models.Profile
import app.eduroam.geteduroam.organizations.ConfiguredOrganization
import app.eduroam.geteduroam.ui.ErrorData

data class UiState(
    val profiles: List<PresentProfile> = emptyList(),
    val organization: PresentOrganization? = null,
    val configuredOrganization: ConfiguredOrganization? = null,
    val providerInfo: ProviderInfo? = null,
    val inProgress: Boolean = false,
    val errorData: ErrorData? = null,
    val promptForOAuth: Boolean = false,
    val checkProfileWhenResuming: Boolean = false,
    val showTermsOfUseDialog: Boolean = false,
    val goToConfigScreenWithProviderList: EAPIdentityProviderList? = null,
    val openUrlInBrowser: String? = null,
    val profileExpiryTimestampMs: Long? = null,
    val showAlertForConfiguringDifferentProfile: PresentProfile? = null
)

data class PresentProfile(val profile: Profile, val isConfigured: Boolean = false, val isSelected: Boolean = false)

data class PresentOrganization(
    val name: String? = null,
    val location: String? = null,
    val displayName: String? = null,
    val description: String? = null,
    val logo: String? = null,
    val termsOfUse: String? = null,
    val helpDesk: Helpdesk? = null
)