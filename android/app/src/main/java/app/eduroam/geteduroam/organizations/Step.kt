package app.eduroam.geteduroam.organizations

import app.eduroam.geteduroam.config.model.EAPIdentityProviderList
import app.eduroam.geteduroam.models.Configuration
import app.eduroam.geteduroam.models.Organization
import app.eduroam.geteduroam.models.ConfigSource

sealed class Step {
    object Start : Step()
    data class DoConfig(
        val configuredOrganization: ConfiguredOrganization,
        val configuredProfileId: String?,
        val eapIdentityProviderList: EAPIdentityProviderList
    ) : Step()
    data class DoOAuthFor(val configuration: Configuration) : Step()
    data class PickProfileFrom(val organization: Organization) : Step()
}