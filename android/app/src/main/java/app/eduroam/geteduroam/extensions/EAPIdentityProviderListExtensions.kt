package app.eduroam.geteduroam.extensions

import app.eduroam.geteduroam.config.model.EAPIdentityProviderList
import timber.log.Timber

fun EAPIdentityProviderList.stripLogos(): EAPIdentityProviderList {
    val originalLogoSize = this.eapIdentityProvider?.firstOrNull()?.providerInfo?.providerLogo?.value?.length ?: 0
    
    val strippedProviders = this.eapIdentityProvider?.map { provider ->
        provider.copy(
            providerInfo = provider.providerInfo?.copy(
                providerLogo = null
            )
        )
    }
    
    val result = this.copy(eapIdentityProvider = strippedProviders)
    
    if (originalLogoSize > 0) {
        Timber.d("EAPIdentityProviderList: Stripped logo of $originalLogoSize characters (~${originalLogoSize / 1024}KB)")
    }
    
    return result
}
