package app.eduroam.geteduroam.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Parcelize
@Serializable
data class LocalizedName(
    val display: String,
    val lang: String? = null
) : Parcelable
