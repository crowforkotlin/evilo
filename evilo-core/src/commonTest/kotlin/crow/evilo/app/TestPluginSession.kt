package crow.evilo.app

import crow.evilo.feature.reader.MediaSourceSession
import crow.evilo.feature.reader.PluginSessionClosedException
import crow.evilo.model.MediaStream
import crow.evilo.plugin.api.MediaSource
import crow.evilo.plugin.api.PluginDescriptor
import crow.evilo.plugin.api.PluginOperation
import crow.evilo.plugin.api.PluginResult
import crow.evilo.plugin.api.SourceRequest

internal class TestPluginSession(descriptor: PluginDescriptor, private val closeFailure: Exception? = null) : MediaSourceSession {
    var closeCalls = 0
    override val source = object : MediaSource {
        override val descriptor = descriptor
        override suspend fun execute(operation: PluginOperation): PluginResult = error("This fixture does not execute works requests.")
        override suspend fun close() = Unit
    }
    override fun approveImageRequest(url: String, headers: Map<String, String>): SourceRequest {
        if (closeCalls > 0) throw PluginSessionClosedException()
        return SourceRequest(url = url, headers = headers.toMap())
    }
    override suspend fun imageBytes(url: String, headers: Map<String, String>): ByteArray = error("This fixture does not request images.")
    override fun externalStreamUrl(stream: MediaStream): String = stream.url
    override suspend fun close() { closeCalls++; closeFailure?.let { throw it } }
}
