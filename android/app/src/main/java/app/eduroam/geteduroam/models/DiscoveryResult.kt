package app.eduroam.geteduroam.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Parcelize
@Serializable
data class DiscoveryResult(
    @SerialName("http://letswifi.app/discovery#v3")
    val content: DiscoveryContent
) : Parcelable
