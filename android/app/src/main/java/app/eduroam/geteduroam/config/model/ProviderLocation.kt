package app.eduroam.geteduroam.config.model

import com.squareup.moshi.JsonClass
import kotlinx.serialization.Serializable
import org.simpleframework.xml.Element
import org.simpleframework.xml.Root

@Root(name = "ProviderLocation")
@JsonClass(generateAdapter = true)
@Serializable
class ProviderLocation {

    @field:Element(name = "Longitude", required = false)
    var longitude: String? = null

    @field:Element(name = "Latitude", required = false)
    var latitude: String? = null
}