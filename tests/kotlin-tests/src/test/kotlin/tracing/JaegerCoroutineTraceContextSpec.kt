package tracing

import io.jaegertracing.internal.JaegerSpan
import io.jaegertracing.internal.metrics.InMemoryMetricsFactory
import io.jaegertracing.internal.reporters.InMemoryReporter
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.tracing.annotation.NewSpan
import io.opentracing.Tracer
import jakarta.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Re-tests micronaut-tracing#940 with an OpenTracing tracer (the original report used Zipkin/Brave through OpenTracing).
 */
class JaegerCoroutineTraceContextSpec {

    private lateinit var server: EmbeddedServer
    private lateinit var client: HttpClient
    private lateinit var reporter: InMemoryReporter

    @BeforeEach
    fun setup() {
        server = ApplicationContext.builder(
            mapOf(
                "coroutinejaeger.enabled" to "true",
                "otel.enabled" to "false",
                "tracing.jaeger.enabled" to "true",
                "tracing.jaeger.sampler.probability" to "1",
                "micronaut.http.client.read-timeout" to "30s"
            )
        ).singletons(InMemoryReporter(), InMemoryMetricsFactory()).run(EmbeddedServer::class.java)
        client = HttpClient.create(server.url)
        reporter = server.applicationContext.getBean(InMemoryReporter::class.java)
    }

    @AfterEach
    fun cleanup() {
        client.close()
        server.stop()
    }

    @Test
    fun `issue 940 - OpenTracing NewSpan suspend functions and downstream client call stay in one trace`() {
        val response = client.toBlocking().retrieve("/coroutine-jaeger/async-bar", String::class.java)
        val (routineSpanIdSeen, downstreamTraceId) = response.split(",")

        val spans = awaitSpans(6)
        val serverSpan = spans.single { it.tags["http.server"] == true && it.operationName.contains("async-bar") }
        val asyncBar = spans.single { it.operationName.endsWith("asyncBar") }
        val someAsyncWork = spans.single { it.operationName.endsWith("someAsyncWork") }
        val someRoutineWork = spans.single { it.operationName.endsWith("someRoutineWork") }
        val clientSpan = spans.single { it.tags["http.client"] == true }

        assertEquals(1, spans.map { it.context().traceId }.toSet().size, "all spans must be in one trace: $spans")
        assertEquals(serverSpan.context().toTraceId(), downstreamTraceId)
        assertEquals(serverSpan.context().spanId, asyncBar.context().parentId)
        assertEquals(asyncBar.context().spanId, someAsyncWork.context().parentId)
        assertEquals(someAsyncWork.context().spanId, someRoutineWork.context().parentId)
        assertEquals(someRoutineWork.context().toSpanId(), routineSpanIdSeen, "tracer.activeSpan() inside @NewSpan suspend fun after delay")
        assertEquals(someAsyncWork.context().spanId, clientSpan.context().parentId)
    }

    private fun awaitSpans(min: Int): List<JaegerSpan> {
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline && reporter.spans.size < min) {
            Thread.sleep(50)
        }
        Thread.sleep(200)
        val spans = reporter.spans
        assertTrue(spans.size >= min, "expected at least $min spans but got ${spans.size}: $spans")
        return spans
    }
}

@Requires(property = "coroutinejaeger.enabled")
@Controller("/coroutine-jaeger")
open class JaegerCoroutineTraceController(
    private val tracer: Tracer,
    private val service: JaegerCoroutineTraceService
) {

    @NewSpan
    @Get("/async-bar")
    open suspend fun asyncBar(): String = service.someAsyncWork()

    @Get("/downstream")
    open suspend fun downstream(): String {
        delay(5)
        return tracer.activeSpan()?.context()?.toTraceId() ?: "none"
    }
}

@Requires(property = "coroutinejaeger.enabled")
@Singleton
open class JaegerCoroutineTraceService(
    private val tracer: Tracer,
    private val downstreamClient: JaegerCoroutineTraceDownstreamClient
) {

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
        tracer.activeSpan()?.context()?.toSpanId() ?: "none"
    }
}

@Requires(property = "coroutinejaeger.enabled")
@Client("/coroutine-jaeger")
interface JaegerCoroutineTraceDownstreamClient {

    @Get("/downstream")
    suspend fun downstream(): String
}
