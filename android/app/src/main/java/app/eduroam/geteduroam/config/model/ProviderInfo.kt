package app.eduroam.geteduroam.config.model

import kotlinx.serialization.Serializable
import org.simpleframework.xml.Attribute
import org.simpleframework.xml.Element
import org.simpleframework.xml.ElementList
import org.simpleframework.xml.Root
import org.simpleframework.xml.Text
import java.util.Locale

@Root(name = "ProviderInfo")
@Serializable
data class ProviderInfo(
    @field:ElementList(inline = true, entry = "DisplayName", required = false)
    var displayNames: List<LocalizedText>? = null,

    @field:ElementList(inline = true, entry = "Description", required = false)
    var descriptions: List<LocalizedText>? = null,

    @field:ElementList(inline = true, entry = "ProviderLocation", required = false)
    var providerLocations: List<ProviderLocation>? = null,

    @field:Element(name = "ProviderLogo", required = false)
    var providerLogo: ProviderLogo? = null,

    @field:ElementList(inline = true, entry = "TermsOfUse", required = false)
    var termsOfUse: List<LocalizedText>? = null,

    @field:Element(name = "Helpdesk", required = false)
    var helpdesk: Helpdesk? = null
)


@Serializable
data class LocalizedText(
    // The 'lang' attribute of the XML tag
    @field:Attribute(name = "lang", required = false)
    var lang: String? = null,

    // The text content of the XML tag
    @field:Text
    var value: String = ""
)

fun List<LocalizedText>.localizedMatch(): String? {
    val language = Locale.getDefault().language.lowercase()
    // Find the first match for the specified language
    val match = this.firstOrNull { it.lang.equals(language, ignoreCase = true) }
    if (match != null) {
        return match.value
    }
    // If no match is found, return the first available value
    return this.firstOrNull()?.value
}
