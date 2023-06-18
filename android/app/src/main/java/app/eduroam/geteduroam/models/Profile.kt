package app.eduroam.geteduroam.models

import android.os.Parcelable
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import kotlinx.parcelize.Parcelize

@Parcelize
@JsonClass(generateAdapter = true)
data class Profile(
    @Json(name = "eapconfig_endpoint")
    val eapconfigEndpoint: String? = null,
    val id: String,
    val name: String,
    val oauth: Boolean = false,
    @Json(name = "authorization_endpoint")
    val authorizationEndpoint: String? = null,
    @Json(name = "token_endpoint")
    val tokenEndpoint: String? = null,
) : Parcelable

