package app.eduroam.geteduroam.config.model

import kotlinx.serialization.Serializable
import org.simpleframework.xml.Attribute
import org.simpleframework.xml.Root
import org.simpleframework.xml.Text

@Root(name = "CA")
@Serializable
class CertData {

    @field:Text
    var value: String? = null

    @field:Attribute
    var format: String? = null

    @field:Attribute
    var encoding: String? = null

    fun isSupported() = format == "X.509" && encoding == "base64" && !value.isNullOrEmpty()
}