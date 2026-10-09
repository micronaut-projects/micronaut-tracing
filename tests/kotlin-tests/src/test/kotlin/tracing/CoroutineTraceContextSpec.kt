package tracing

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.tracing.annotation.NewSpan
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SpanProcessor
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import jakarta.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Re-tests micronaut-tracing#210 (OpenTelemetry context lost after the first coroutine suspension)
 * and micronaut-tracing#940 (downstream HTTP calls from coroutines end up in separate traces).
 */
class CoroutineTraceContextSpec {

    private lateinit var server: EmbeddedServer
    private lateinit var client: HttpClient
    private lateinit var exporter: InMemorySpanExporter

    @BeforeEach
    fun setup() {
        server = ApplicationContext.run(
            EmbeddedServer::class.java,
            mapOf(
                "coroutinetracecontext.enabled" to "true",
                "micronaut.application.name" to "coroutine-trace-context",
                "otel.traces.exporter" to "none",
                "otel.metrics.exporter" to "none",
                "otel.logs.exporter" to "none",
                "otel.register.global" to "false",
                "micronaut.http.client.read-timeout" to "30s"
            )
        )
        client = HttpClient.create(server.url)
        exporter = server.applicationContext.getBean(InMemorySpanExporter::class.java)
    }

    @AfterEach
    fun cleanup() {
        client.close()
        server.stop()
    }

    // #210: suspend controller, delay(), dispatcher switches, then Span.current() and a client call
    @Test
    fun `issue 210 - OpenTelemetry context survives coroutine suspension and dispatcher switches`() {
        val response = client.toBlocking().retrieve("/coroutine-trace/suspend", String::class.java)
        val ids = response.split(",")

        val spans = awaitSpans(3)
        val serverSpan = spans.single { it.kind == SpanKind.SERVER && it.name.contains("/coroutine-trace/suspend") }
        val traceId = serverSpan.traceId

        assertEquals(listOf(traceId, traceId, traceId, traceId, traceId), ids,
            "trace ids seen: before, after delay, on Default, on IO, downstream server")
        assertEquals(1, spans.map { it.traceId }.toSet().size, "all spans must be in one trace: $spans")

        val clientSpan = spans.single { it.kind == SpanKind.CLIENT }
        assertEquals(serverSpan.spanId, clientSpan.parentSpanId)
        val downstreamServer = spans.single { it.kind == SpanKind.SERVER && it.name.contains("downstream") }
        assertEquals(clientSpan.spanId, downstreamServer.parentSpanId)
    }

    // #940 / #210: @NewSpan suspend functions (mirrors bjor-joh/micronaut-distributed-tracing)
    @Test
    fun `issue 940 - NewSpan suspend functions and downstream client call stay in one trace`() {
        val response = client.toBlocking().retrieve("/coroutine-trace/async-bar", String::class.java)
        val (routineSpanIdSeen, downstreamTraceId) = response.split(",")

        val spans = awaitSpans(6)
        val serverSpan = spans.single { it.kind == SpanKind.SERVER && it.name.contains("async-bar") }
        val asyncBar = spans.single { it.name.endsWith("asyncBar") }
        val someAsyncWork = spans.single { it.name.endsWith("someAsyncWork") }
        val someRoutineWork = spans.single { it.name.endsWith("someRoutineWork") }
        val clientSpan = spans.single { it.kind == SpanKind.CLIENT }

        assertEquals(1, spans.map { it.traceId }.toSet().size, "all spans must be in one trace: $spans")
        assertEquals(serverSpan.traceId, downstreamTraceId)
        assertEquals(serverSpan.spanId, asyncBar.parentSpanId)
        assertEquals(asyncBar.spanId, someAsyncWork.parentSpanId)
        assertEquals(someAsyncWork.spanId, someRoutineWork.parentSpanId)
        assertEquals(someRoutineWork.spanId, routineSpanIdSeen, "Span.current() inside @NewSpan suspend fun after delay")
        assertEquals(someAsyncWork.spanId, clientSpan.parentSpanId)
    }

    private fun awaitSpans(min: Int): List<SpanData> {
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            val spans = exporter.finishedSpanItems
            if (spans.size >= min) {
                Thread.sleep(200)
                return exporter.finishedSpanItems
            }
            Thread.sleep(50)
        }
        val spans = exporter.finishedSpanItems
        assertNotNull(null, "expected at least $min spans but got ${spans.size}: $spans")
        return spans
    }
}

@Factory
@Requires(property = "coroutinetracecontext.enabled")
class CoroutineTraceContextExporterFactory {

    @Singleton
    fun exporter(): InMemorySpanExporter = InMemorySpanExporter.create()

    @Singleton
    fun processor(exporter: InMemorySpanExporter): SpanProcessor = SimpleSpanProcessor.create(exporter)
}

@Requires(property = "coroutinetracecontext.enabled")
@Controller("/coroutine-trace")
open class CoroutineTraceController(
    private val downstreamClient: CoroutineTraceDownstreamClient,
    private val service: CoroutineTraceService
) {

    @Get("/suspend")
    open suspend fun suspendEndpoint(): String {
        val before = Span.current().spanContext.traceId
        delay(20)
        val afterDelay = Span.current().spanContext.traceId
        val onDefault = withContext(Dispatchers.Default) {
            delay(20)
            Span.current().spanContext.traceId
        }
        val (onIo, downstream) = withContext(Dispatchers.IO) {
            delay(20)
            Span.current().spanContext.traceId to downstreamClient.downstream()
        }
        return listOf(before, afterDelay, onDefault, onIo, downstream).joinToString(",")
    }

    @NewSpan
    @Get("/async-bar")
    open suspend fun asyncBar(): String = service.someAsyncWork()

    @Get("/downstream")
    open suspend fun downstream(): String {
        delay(5)
        return Span.current().spanContext.traceId
    }
}

@Requires(property = "coroutinetracecontext.enabled")
@Singleton
open class CoroutineTraceService(private val downstreamClient: CoroutineTraceDownstreamClient) {

    @NewSpan
    open suspend fun someAsyncWork(): String = withContext(Dispatchers.Default) {
        delay(50)
        val routineSpanId = someRoutineWork()
        val downstreamTraceId = downstreamClient.downstream()
        "$routineSpanId,$downstreamTraceId"
    }

    @NewSpan
    open suspend fun someRoutineWork(): String = withContext(Dispatchers.Default) {
        delay(20)
        Span.current().spanContext.spanId
    }
}

@Requires(property = "coroutinetracecontext.enabled")
@Client("/coroutine-trace")
interface CoroutineTraceDownstreamClient {

    @Get("/downstream")
    suspend fun downstream(): String
}
