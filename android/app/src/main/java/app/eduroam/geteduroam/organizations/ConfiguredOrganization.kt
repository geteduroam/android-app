package app.eduroam.geteduroam.organizations

import app.eduroam.geteduroam.models.ConfigSource
import kotlinx.serialization.Serializable

@Serializable
data class ConfiguredOrganization(
    val source: ConfigSource,
    val id: String,
    val name: String?,
    val country: String?
) {
    companion object {
        const val ID_ORGANIZATION_FROM_FILE = "eduroam_organization_from_file"
    }
}
