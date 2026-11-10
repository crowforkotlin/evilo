package crow.evilo.plugin.api

fun ProtocolRequirement.incompatibility(host: HostDescriptor): String? = when {
    major != host.protocolMajor -> "Plugin protocol $major is not supported; host protocol is ${host.protocolMajor}."
    minimumMinor !in 0..host.protocolMinor -> "Plugin requires protocol $major.$minimumMinor or newer."
    !host.capabilities.containsAll(requiredCapabilities) ->
        "Missing host capabilities: ${(requiredCapabilities - host.capabilities).sorted().joinToString()}."
    else -> null
}
