package app.eduroam.geteduroam.config

import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiEnterpriseConfig
import android.net.wifi.WifiEnterpriseConfig.Eap
import android.net.wifi.WifiEnterpriseConfig.Phase2
import android.net.wifi.WifiNetworkSuggestion
import android.net.wifi.hotspot2.PasspointConfiguration
import android.net.wifi.hotspot2.pps.Credential
import android.net.wifi.hotspot2.pps.HomeSp
import android.os.Build
import android.util.Base64
import androidx.annotation.RequiresApi
import app.eduroam.geteduroam.config.model.EAPIdentityProvider
import app.eduroam.geteduroam.config.model.EAPIdentityProviderList
import app.eduroam.geteduroam.config.model.bestMethod
import timber.log.Timber
import java.nio.charset.Charset


@RequiresApi(Build.VERSION_CODES.R)
fun EAPIdentityProviderList.buildAllNetworkSuggestions(): List<WifiNetworkSuggestion> {
    val suggestions = buildSSIDSuggestions()
    val passpointSuggestions = buildPasspointSuggestion()
    return if (passpointSuggestions != null) {
        suggestions + passpointSuggestions
    } else {
        suggestions
    }

}

/**
 * Create SSID-based network suggestions for this profile
 *
 * This will return one suggestion per SSID.  The resulting list is generated on the fly,
 * and may be safely modified by the caller.
 *
 * @return List of network suggestions, one per SSID
 * @see this.buildPasspointSuggestion
 * @see this.buildNetworkRequests
 */
@RequiresApi(Build.VERSION_CODES.Q)
fun EAPIdentityProviderList.buildSSIDSuggestions(): List<WifiNetworkSuggestion> {
    val eapIdentityProvider = eapIdentityProvider?.first()
    val ssids = eapIdentityProvider?.credentialApplicability?.map { it.ssid } ?: listOf()
    // Initial capacity = amount of SSIDs + 1, to keep room for a a Passpoint configuration
    val enterpriseConfig = buildEnterpriseConfig()
    return ssids.filterNotNull().map { ssid ->
        WifiNetworkSuggestion.Builder()
            .setSsid((ssid))
            .setWpa2EnterpriseConfig(enterpriseConfig)
            .setIsAppInteractionRequired(true)
            .build()
    }
}

/**
 * Create Passpoint-based network suggestion for this profile
 *
 * A Passpoint suggestion can contain multiple OIDs, so the whole profile will always fit
 * in a single suggestions.
 *
 * If there are no OIDs in this profile, this function will return NULL
 *
 * @return Network suggestion for Passpoint
 * @see this.buildSSIDSuggestions
 * @see this.buildNetworkRequests
 */
@RequiresApi(api = Build.VERSION_CODES.R)
fun EAPIdentityProviderList.buildPasspointSuggestion(): WifiNetworkSuggestion? {
    val passpointConfig = buildPasspointConfig()
    return if (passpointConfig != null) {
        WifiNetworkSuggestion.Builder().setPasspointConfig(passpointConfig).build()
    } else {
        null
    }
}

/**
 * Create a WifiConfiguration object which can be installed on API target 28
 *
 * @return List of Wi-Fi configurations
 */
@Suppress("DEPRECATION")
fun EAPIdentityProviderList.buildWifiConfigurations(): List<WifiConfiguration> {
    val eapIdentityProvider = eapIdentityProvider?.first()
    val ssids = eapIdentityProvider?.credentialApplicability?.map { it.ssid } ?: listOf()
    val enterpriseConfig = buildEnterpriseConfig()
    return ssids.map { ssid ->
        val config = WifiConfiguration()
        config.SSID = "\"$ssid\""
        config.priority = 1
        config.status = WifiConfiguration.Status.ENABLED
        config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_EAP)
        config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.IEEE8021X)
        config.enterpriseConfig = enterpriseConfig
        config
    }
}

private fun EAPIdentityProviderList.buildEnterpriseConfig(): WifiEnterpriseConfig {
    val eapIdentityProvider = eapIdentityProvider?.first()
    val authMethod = eapIdentityProvider?.authenticationMethod?.bestMethod()
    val enterpriseConfig = WifiEnterpriseConfig()
    enterpriseConfig.anonymousIdentity = authMethod?.clientSideCredential?.outerIdentity

    val enterpriseEAP = authMethod?.eapMethod?.type?.toInt()?.convertEAPMethod() ?: Eap.NONE
    enterpriseConfig.eapMethod = enterpriseEAP

    val caCertificates = authMethod?.serverSideCredential
        ?.certData
        ?.filter { it.isSupported() }
        ?.mapNotNull { it.value }
    enterpriseConfig.caCertificates = getCertificates(caCertificates)
        .filter { isCA(it) }
        .toTypedArray()

    val serverNames = authMethod?.serverSideCredential?.serverId
    assert(
        (serverNames?.size != 0) // Checked in WifiProfile constructor
    )
    enterpriseConfig.domainSuffixMatch = getServerNamesDomainDependentOnAndroidVersion()

    // Explicitly reset client certificate, will set later if needed
    enterpriseConfig.setClientKeyEntry(null, null)
    when (enterpriseEAP) {
        Eap.TLS -> {
            handleEapTLS(enterpriseConfig)
        }

        Eap.PEAP, Eap.TTLS, Eap.PWD -> {
            handleOtherEap(enterpriseConfig)
        }

        else -> throw IllegalArgumentException("Invalid EAP type $enterpriseEAP")
    }
    return enterpriseConfig
}

fun EAPIdentityProvider.requiresUsernamePrompt(): Boolean {
    val eapMethod = authenticationMethod?.bestMethod()?.eapMethod?.type?.toInt()?.convertEAPMethod()
    return  eapMethod == Eap.TTLS || eapMethod == Eap.PEAP || eapMethod == Eap.PWD
}

fun EAPIdentityProvider.requiredSuffix(): String? {
    return authenticationMethod?.bestMethod()?.clientSideCredential?.innerIdentitySuffix
}

fun EAPIdentityProvider.enforceRequiredSuffix(): Boolean {
    return authenticationMethod?.bestMethod()?.clientSideCredential?.innerIdentityHint == "true"
}

/**
 * Converts an internal EAP method type to the one used by Android
 */
fun Int.convertEAPMethod(): Int {
    return when (this) {
        13 -> Eap.TLS
        21 -> Eap.TTLS
        25 -> Eap.PEAP
        43 -> Eap.TLS // EAP-FAST
        52 -> Eap.PWD
        else -> Eap.NONE
    }
}

private fun EAPIdentityProviderList.handleOtherEap(enterpriseConfig: WifiEnterpriseConfig) {
    val eapIdentityProvider = eapIdentityProvider?.first()
    val username =
        eapIdentityProvider?.authenticationMethod?.bestMethod()?.clientSideCredential?.userName
    val password =
        eapIdentityProvider?.authenticationMethod?.bestMethod()?.clientSideCredential?.password
    val enterpriseEAP = eapIdentityProvider?.authenticationMethod?.bestMethod()?.eapMethod?.type?.toInt()?.convertEAPMethod() ?: Eap.NONE
    val enterprisePhase2Auth = eapIdentityProvider?.getPhase2AuthType(enterpriseEAP)

    enterpriseConfig.identity = username
    enterpriseConfig.password = password
    enterpriseConfig.phase2Method = enterprisePhase2Auth ?: Phase2.NONE
}

private fun EAPIdentityProviderList.handleEapTLS(enterpriseConfig: WifiEnterpriseConfig) {
    // Explicitly unset unused field
    val eapIdentityProvider = eapIdentityProvider?.first()

    enterpriseConfig.password = ""
    enterpriseConfig.phase2Method = WifiEnterpriseConfig.Phase2.NONE
    val clientCert =
        eapIdentityProvider?.authenticationMethod?.bestMethod()?.clientSideCredential?.getClientCertificate()
    if (clientCert?.key != null) {
        enterpriseConfig.setClientKeyEntry(
            clientCert.key, clientCert.value[0]
        )
    }
    // For TLS, "identity" is used for outer identity,
    // while for PEAP/TTLS, "identity" is the inner identity,
    // and anonymousIdentity is the outer identity
    // - so we have to do some weird shuffling here.
    val anonymousIdentity =
        eapIdentityProvider?.authenticationMethod?.bestMethod()?.clientSideCredential?.outerIdentity
    enterpriseConfig.identity = anonymousIdentity
}

/**
 * Get the string that Android uses for server name validation.
 *
 * Server names are treated as suffix, but exact string match is also accepted.
 *
 * On Android 9, only a single name is supported.
 * Thus, for Android 9, we will calculate the longest suffix match.
 *
 * On Android 10 and onwards, the string can be semicolon-separated,
 * which is what we will do for these platforms.
 *
 * @return The server name
 */
private fun EAPIdentityProviderList.getServerNamesDomainDependentOnAndroidVersion(): String? {
    val eapIdentityProvider = eapIdentityProvider?.first()
    val serverNames = eapIdentityProvider?.authenticationMethod?.bestMethod()?.serverSideCredential?.serverId ?: emptyList()
    val joinedServerNames = serverNames.joinToString(";")
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && joinedServerNames.length < 256) {
        joinedServerNames
    } else {
        getLongestSuffix(serverNames)
    }
}

/**
 * Create the configuration necessary to configure a passpoint and returns it
 *
 * @return Passpoint configuration for this profile
 * @see this.buildEnterpriseConfig
 */
fun EAPIdentityProviderList.buildPasspointConfig(): PasspointConfiguration? {
    val eapIdentityProvider = eapIdentityProvider?.first()
    val caCertificates = eapIdentityProvider?.authenticationMethod
        ?.bestMethod()
        ?.serverSideCredential
        ?.certData
        ?.filter { it.isSupported() }
        ?.mapNotNull { it.value }
    val oids = eapIdentityProvider?.credentialApplicability?.map { it.consortiumOID?.toLong(16) }?.filterNotNull() ?: listOf()
    val fqdn: String? = eapIdentityProvider?.ID

    if (oids.isEmpty()) {
        Timber.i("Not creating Passpoint configuration due to no OIDs set")
        return null
    }
    val passpointConfig = PasspointConfiguration()
    val homeSp = setupHomeSp()
    passpointConfig.homeSp = homeSp
    val cred = Credential()
    val certificates = getCertificates(caCertificates)
    val rootCertificates = certificates.filter { isRootCertificate(it) }
    // For now we only support single CA
    if (rootCertificates.isNotEmpty() && rootCertificates.size == 1) {
        // Just use the first CA for Passpoint
        cred.caCertificate = rootCertificates[0]
    } else {
        Timber.w("Not creating Passpoint configuration due to too many CAs in the profile (1 supported, ${rootCertificates.size} given)")
        return null
    }
    cred.realm = fqdn
    val enterpriseEAP = eapIdentityProvider?.authenticationMethod?.bestMethod()?.eapMethod?.type?.toInt()?.convertEAPMethod() ?: Eap.NONE
    val enterprisePhase2Auth = eapIdentityProvider?.getPhase2AuthType(enterpriseEAP)

    when (enterpriseEAP) {
        Eap.TLS -> {
            val certCred = Credential.CertificateCredential()
            val clientCert =
                    eapIdentityProvider?.authenticationMethod?.bestMethod()?.clientSideCredential?.getClientCertificate()
            if (clientCert?.key != null) {
                certCred.certType = "x509v3"
                cred.clientPrivateKey = clientCert.key
                cred.clientCertificateChain = clientCert.value
                certCred.certSha256Fingerprint = getFingerprint(clientCert.value[0])
                cred.certCredential = certCred
            }
        }

        Eap.PWD -> {
            Timber.i("Not creating Passpoint configuration due to unsupported EAP type $enterpriseEAP")
            return null // known but unsupported EAP method
        }

        Eap.PEAP, Eap.TTLS -> {
            val username =
                eapIdentityProvider?.authenticationMethod?.bestMethod()?.clientSideCredential?.userName
            val password =
                eapIdentityProvider?.authenticationMethod?.bestMethod()?.clientSideCredential?.password
            val passwordBytes =
                password!!.toByteArray(Charset.defaultCharset()) // TODO explicitly use UTF-8?
            val base64 = Base64.encodeToString(passwordBytes, Base64.DEFAULT)
            val us = Credential.UserCredential()
            us.username = username
            us.password = base64
            us.eapType = 21 // 21 indicates TTLS (RFC 5281)
            when (enterprisePhase2Auth) {
                Phase2.MSCHAPV2 -> us.nonEapInnerMethod = "MS-CHAP-V2"
                Phase2.PAP -> us.nonEapInnerMethod = "PAP"
                Phase2.MSCHAP -> us.nonEapInnerMethod = "MS-CHAP"
                else -> us.nonEapInnerMethod = null
            }
            cred.userCredential = us
        }

        else -> throw IllegalArgumentException("Invalid EAP type $enterpriseEAP")
    }
    passpointConfig.credential = cred
    return passpointConfig
}

private fun EAPIdentityProvider.getPhase2AuthType(eapMethod: Int): Int? {
    if (eapMethod == Eap.TLS) {
        return getInnerAuthMethod(0)
    } else {
        val validAuthMethod = authenticationMethod?.bestMethod()
        val innerNonEapMethod = validAuthMethod?.innerAuthenticationMethod?.nonEapMethod?.type?.toInt()?.times(-1)
        val innerEapMethod = validAuthMethod?.innerAuthenticationMethod?.eapMethod?.type?.toInt()
        return if (innerEapMethod != null && innerEapMethod != 0) {
            getInnerAuthMethod(innerEapMethod)
        } else {
            getInnerAuthMethod(innerNonEapMethod)
        }
    }
}

/**
 * Converts a eap-config/CAT inner type to an Android Phase2 integer
 *
 * @param authMethod Auth method as used in eap-config
 * @return ENUM from WifiEnterpriseConfig.Phase2 (PAP/MSCHAP/MSCHAPv2) or -1 if no match
 */
private fun getInnerAuthMethod(authMethod: Int?): Int? {
    return when (authMethod) {
        -1 -> WifiEnterpriseConfig.Phase2.PAP
        -2 -> WifiEnterpriseConfig.Phase2.MSCHAP
        -3, 26 ->
            // This currently DOES happen because CAT has a bug where it reports TTLS-MSCHAPv2 as TTLS-EAP-MSCHAPv2,
            // so denying this would prevent profiles from being side-loaded
            WifiEnterpriseConfig.Phase2.MSCHAPV2
        else -> null
    }
}

private fun EAPIdentityProviderList.setupHomeSp(): HomeSp {
    val homeSp = HomeSp()
    val eapIdentityProvider = eapIdentityProvider?.first()
    val oids = eapIdentityProvider?.credentialApplicability?.map { it.consortiumOID?.toLong(16) }
        ?.filterNotNull() ?: listOf()

    // The FQDN in this case is the server names being used to verify the server certificate
    // Passpoint also has a domain, which is set later with Credential.setRealm(fqdn)
    homeSp.fqdn = getServerNamesDomainDependentOnAndroidVersion()
    homeSp.friendlyName = "${eapIdentityProvider?.ID} via Passpoint"
    homeSp.roamingConsortiumOis = oids.toLongArray()
    return homeSp
}

fun getLongestSuffix(strings: List<String?>?): String? {
    if (strings.isNullOrEmpty()) return ""
    if (strings.size == 1) return strings[0]
    var longest = strings[0]
    for (candidate: String? in strings) {
        var pos = candidate!!.length
        do {
            pos = candidate.lastIndexOf('.', pos - 2) + 1
        } while (pos > 0 && longest!!.endsWith(candidate.substring(pos)))
        if (!longest!!.endsWith(candidate.substring(pos))) {
            pos = candidate.indexOf('.', pos)
        }
        if (pos == -1) {
            longest = ""
        } else if (longest.endsWith(candidate.substring(pos))) {
            longest = candidate.substring(if (pos == 0) 0 else pos + 1)
        }
    }
    return longest
}


private const val TAG = "PlatformWifiConfigData"