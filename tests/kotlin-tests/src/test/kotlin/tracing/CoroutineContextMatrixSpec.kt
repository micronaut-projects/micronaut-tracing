package tracing

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.core.async.propagation.KotlinCoroutinePropagation
import io.micronaut.core.propagation.PropagatedContextConfiguration
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
import jakarta.inject.Named
import jakarta.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.util.concurrent.Executors
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Context propagation cases not covered by [CoroutineTraceContextSpec]: a dispatcher with limited parallelism and
 * coroutines started in a custom scope, with and without Micronaut's propagated context element.
 */
open class CoroutineContextMatrixSpec {

    private lateinit var server: EmbeddedServer
    private lateinit var client: HttpClient
    private lateinit var exporter: InMemorySpanExporter

    protected open fun configuration(): Map<String, Any> = mapOf(
        "coroutinecontextmatrix.enabled" to "true",
        "micronaut.application.name" to "coroutine-context-matrix",
        "otel.traces.exporter" to "none",
        "otel.metrics.exporter" to "none",
        "otel.logs.exporter" to "none",
        "otel.register.global" to "false",
        "micronaut.http.client.read-timeout" to "30s"
    )

    @BeforeEach
    fun setup() {
        server = ApplicationContext.run(EmbeddedServer::class.java, configuration())
        client = HttpClient.create(server.url)
        exporter = server.applicationContext.getBean(InMemorySpanExporter::class.java)
    }

    @AfterEach
    fun cleanup() {
        client.close()
        server.stop()
    }

    @Test
    fun `withContext on Dispatchers IO limitedParallelism keeps the trace and the parent of the client call`() {
        val (spanId, downstreamTraceId) = get("/coroutine-matrix/limited").split(",")

        val spans = awaitSpans(3)
        val serverSpan = spans.single { it.kind == SpanKind.SERVER && it.name.endsWith("/coroutine-matrix/limited") }
        val clientSpan = spans.single { it.kind == SpanKind.CLIENT }
        val downstream = spans.single { it.kind == SpanKind.SERVER && it.name.endsWith("/downstream") }

        assertEquals(serverSpan.spanId, spanId, "Span.current() on the limited dispatcher")
        assertEquals(serverSpan.traceId, downstreamTraceId)
        assertEquals(serverSpan.spanId, clientSpan.parentSpanId)
        assertEquals(clientSpan.spanId, downstream.parentSpanId)
    }

    @Test
    fun `coroutine started in a custom scope with the propagated context element`() {
        val (spanId, newSpanId, downstreamTraceId) = get("/coroutine-matrix/custom-scope").split(",")

        val spans = awaitSpans(4)
        val serverSpan = spans.single { it.kind == SpanKind.SERVER && it.name.endsWith("/coroutine-matrix/custom-scope") }
        val newSpan = spans.single { it.name.endsWith("newSpanWork") }
        val clientSpan = spans.single { it.kind == SpanKind.CLIENT }

        assertEquals(serverSpan.spanId, spanId, "Span.current() in the coroutine of the custom scope")
        assertEquals(serverSpan.spanId, newSpan.parentSpanId)
        assertEquals(newSpan.spanId, newSpanId)
        assertEquals(newSpan.spanId, clientSpan.parentSpanId)
        assertEquals(serverSpan.traceId, downstreamTraceId)

        val dispatcher = server.applicationContext.getBean(ExecutorCoroutineDispatcher::class.java)
        assertFalse(runBlocking(dispatcher) { Span.current().spanContext.isValid },
            "the single thread of the custom scope is left without the context")
    }

    @Test
    fun `coroutine started in a custom scope without the propagated context element has no trace context`() {
        assertEquals("false", get("/coroutine-matrix/custom-scope-without-context"))
    }

    private fun get(path: String): String = client.toBlocking().retrieve(path, String::class.java)

    private fun awaitSpans(min: Int): List<SpanData> {
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            if (exporter.finishedSpanItems.size >= min) {
                Thread.sleep(200)
                return exporter.finishedSpanItems
            }
            Thread.sleep(50)
        }
        fail<Unit>("expected at least $min spans but got ${exporter.finishedSpanItems}")
        return emptyList()
    }
}

/**
 * Runs [CoroutineContextMatrixSpec] in the scoped-value propagation mode of Micronaut Core 5.3.
 *
 * Disabled: Micronaut Core's coroutine context element (`MicronautPropagatedContext`) brings the propagated
 * context into scope with `PropagatedContext.propagate()`, which throws in the scoped-value mode, so every
 * dispatched suspend function fails with a `CoroutinesInternalError` and the request never completes.
 */
@Disabled("Micronaut Core 5.3: MicronautPropagatedContext uses PropagatedContext.propagate(), unsupported in the scoped-value mode")
class ScopedValueCoroutineContextMatrixSpec : CoroutineContextMatrixSpec() {

    override fun configuration(): Map<String, Any> = super.configuration() + ("micronaut.propagation" to "scoped-value")

    @Test
    fun `the scoped-value propagation mode is active`() {
        assertEquals(PropagatedContextConfiguration.Mode.SCOPED_VALUE, PropagatedContextConfiguration.get())
    }
}

@Factory
@Requires(property = "coroutinecontextmatrix.enabled")
class CoroutineContextMatrixFactory {

    @Singleton
    fun exporter(): InMemorySpanExporter = InMemorySpanExporter.create()

    @Singleton
    fun processor(exporter: InMemorySpanExporter): SpanProcessor = SimpleSpanProcessor.create(exporter)

    @Singleton
    @Named("matrix-single")
    @Bean(preDestroy = "close")
    fun dispatcher(): ExecutorCoroutineDispatcher =
        Executors.newSingleThreadExecutor { Thread(it, "matrix-single") }.asCoroutineDispatcher()
}

@Requires(property = "coroutinecontextmatrix.enabled")
@Controller("/coroutine-matrix")
open class CoroutineContextMatrixController(
    private val downstreamClient: CoroutineContextMatrixClient,
    private val service: CoroutineContextMatrixService,
    @Named("matrix-single") private val dispatcher: ExecutorCoroutineDispatcher
) {

    @Get("/limited")
    open suspend fun limited(): String = withContext(Dispatchers.IO.limitedParallelism(2)) {
        delay(20)
        val spanId = Span.current().spanContext.spanId
        "$spanId,${downstreamClient.downstream()}"
    }

    @Get("/custom-scope")
    open suspend fun customScope(): String {
        val propagatedContext = KotlinCoroutinePropagation.findPropagatedContext()!!
        val scope = CoroutineScope(dispatcher + KotlinCoroutinePropagation.addPropagatedContext(EmptyCoroutineContext, propagatedContext))
        return scope.async {
            delay(20)
            val spanId = Span.current().spanContext.spanId
            "$spanId,${service.newSpanWork()}"
        }.await()
    }

    @Get("/custom-scope-without-context")
    open suspend fun customScopeWithoutContext(): String {
        val scope = CoroutineScope(dispatcher)
        val valid = scope.async {
            delay(20)
            Span.current().spanContext.isValid
        }.await()
        return valid.toString()
    }

    @Get("/downstream")
    open suspend fun downstream(): String {
        delay(5)
        return Span.current().spanContext.traceId
    }
}

@Requires(property = "coroutinecontextmatrix.enabled")
@Singleton
open class CoroutineContextMatrixService(private val downstreamClient: CoroutineContextMatrixClient) {

    @NewSpan
    open suspend fun newSpanWork(): String {
        delay(20)
        return "${Span.current().spanContext.spanId},${downstreamClient.downstream()}"
    }
}

@Requires(property = "coroutinecontextmatrix.enabled")
@Client("/coroutine-matrix")
interface CoroutineContextMatrixClient {

    @Get("/downstream")
    suspend fun downstream(): String
}
