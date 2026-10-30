package app.eduroam.geteduroam.config

import android.net.wifi.WifiEnterpriseConfig.Phase2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ParseNonEapAuthTypeTest {

    @Test
    fun numericTypesGoThroughTheInnerAuthMapping() {
        assertEquals(Phase2.PAP, parseNonEapAuthType("1"))
        assertEquals(Phase2.MSCHAP, parseNonEapAuthType("2"))
        assertEquals(Phase2.MSCHAPV2, parseNonEapAuthType(" 3 "))
    }

    @Test
    fun acceptsPapByName() {
        assertEquals(Phase2.PAP, parseNonEapAuthType("PAP"))
        assertEquals(Phase2.PAP, parseNonEapAuthType("pap"))
    }

    @Test
    fun returnsNullForUnknownOrMissingValues() {
        assertNull(parseNonEapAuthType(null))
        assertNull(parseNonEapAuthType(""))
        assertNull(parseNonEapAuthType("MSCHAP"))
        assertNull(parseNonEapAuthType("0"))
    }
}
