package app.eduroam.geteduroam.models

import android.os.Parcelable
import app.eduroam.geteduroam.BuildConfig
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.Locale

@Parcelize
@Serializable
data class Profile(
    @SerialName("eapconfig_endpoint")
    val eapconfigEndpoint: String? = null,
    val id: String,
    val name: List<LocalizedName> = emptyList(),
    val oauth: Boolean = false,
    @SerialName("authorization_endpoint")
    val authorizationEndpoint: String? = null,
    @SerialName("token_endpoint")
    val tokenEndpoint: String? = null,
    val redirect: String? = null,
    val type: Type = Profile.Type.unknown,
    @SerialName("letswifi_endpoint")
    val letswifiEndpoint: String? = null,
    @SerialName("webview_endpoint")
    val webviewEndpoint: String? = null
) : Parcelable {

    @Serializable
    enum class Type {
        @SerialName("letswifi")
        letswifi,
        @SerialName("eap-config")
        eapConfig,
        @SerialName("webview")
        webview,
        unknown
    }

    fun getLocalizedName(): String {
        val userLanguage = Locale.getDefault().language.lowercase()
        return name.find { it.lang.equals(userLanguage, ignoreCase = true) }?.display ?: // 1st option: the name in the user's language
            name.find { it.lang.isNullOrBlank() }?.display ?: // 2nd option: the name in the fallback language
            name.firstOrNull()?.display ?: // 3rd option: any name we can find
            id // 4th option: the ID, which is always set
    }
    fun createConfiguration() : Configuration {
        return Configuration(
            clientId = BuildConfig.OAUTH_CLIENT_ID,
            scope = "eap-metadata",
            redirect = BuildConfig.OAUTH_REDIRECT_URI,
            authEndpoint = authorizationEndpoint ?: "",
            tokenEndpoint = tokenEndpoint ?: "",
            discovery = BuildConfig.DISCOVERY_BASE_URL + "v1",
            isHttpsRequired = false
        )
    }

}
