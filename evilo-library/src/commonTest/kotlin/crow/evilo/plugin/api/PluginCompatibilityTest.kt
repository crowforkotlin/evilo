package crow.evilo.plugin.api

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PluginCompatibilityTest {
    private val host = HostDescriptor("1.0.0", 1, capabilities = setOf(HOST_MEDIA_CAPABILITY, HOST_HTTP_CAPABILITY))

    @Test
    fun matchingProtocolAndRequiredCapabilitiesAreAccepted() {
        assertNull(ProtocolRequirement(requiredCapabilities = host.capabilities).incompatibility(host))
    }

    @Test
    fun anotherMajorOrANewerRequiredMinorIsRejected() {
        assertNotNull(ProtocolRequirement(major = host.protocolMajor + 1).incompatibility(host))
        assertNotNull(ProtocolRequirement(minimumMinor = host.protocolMinor + 1).incompatibility(host))
        assertNotNull(ProtocolRequirement(minimumMinor = -1).incompatibility(host))
    }

    @Test
    fun missingRequiredCapabilityIsNamedInTheFailure() {
        val reason = ProtocolRequirement(requiredCapabilities = setOf(HOST_STORAGE_CAPABILITY)).incompatibility(host)
        assertTrue(reason.orEmpty().contains(HOST_STORAGE_CAPABILITY))
    }

    @Test
    fun unknownOptionalCapabilityDoesNotPreventLoading() {
        assertNull(ProtocolRequirement(optionalCapabilities = setOf("reader.bookmarks.v1")).incompatibility(host))
    }
}
