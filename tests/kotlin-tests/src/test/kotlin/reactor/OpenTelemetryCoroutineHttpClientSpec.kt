package reactor

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.runtime.server.EmbeddedServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class OpenTelemetryCoroutineHttpClientSpec {

    @Test
    fun testOpenTelemetryClientFilterWithSuspendClient() {
        val embeddedServer = ApplicationContext.run(
            EmbeddedServer::class.java,
            mapOf(
                "otelcoroutineclient.enabled" to "true",
                "micronaut.application.name" to "otel-coroutine-client-test",
                "otel.traces.exporter" to "none",
                "otel.metrics.exporter" to "none",
                "otel.logs.exporter" to "none",
                "otel.register.global" to "false",
                "micronaut.http.client.read-timeout" to "30s"
            )
        )
        val client = HttpClient.create(embeddedServer.url)
        try {
            val response = client.toBlocking().retrieve("/otel-coroutine-client/call", String::class.java)

            assertEquals("ok", response)
        } finally {
            client.close()
            embeddedServer.stop()
        }
    }
}

@Requires(property = "otelcoroutineclient.enabled")
@Controller("/otel-coroutine-client")
class OpenTelemetryCoroutineClientController(private val client: OpenTelemetryCoroutineDownstreamClient) {

    @Get("/call")
    suspend fun call(): String {
        return withContext(Dispatchers.IO) {
            client.downstream()
        }
    }

    @Get("/downstream")
    suspend fun downstream(): String {
        return "ok"
    }
}

@Requires(property = "otelcoroutineclient.enabled")
@Client("/otel-coroutine-client")
interface OpenTelemetryCoroutineDownstreamClient {

    @Get("/downstream")
    suspend fun downstream(): String
}
