package reactor

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Introspected
import io.micronaut.http.HttpRequest
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Filter
import io.micronaut.http.annotation.Filter.MATCH_ALL_PATTERN
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Post
import io.micronaut.http.client.HttpClient
import io.micronaut.http.filter.HttpServerFilter
import io.micronaut.http.filter.ServerFilterChain
import io.micronaut.runtime.server.EmbeddedServer
import jakarta.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactor.ReactorContext
import kotlinx.coroutines.reactor.asCoroutineContext
import kotlinx.coroutines.reactor.mono
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Test
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.util.context.Context
import reactor.util.function.Tuples
import java.util.UUID

class ReactorContextPropagationSpec {

    @Test
    fun testKotlinPropagation() {

        val embeddedServer = ApplicationContext.run(EmbeddedServer::class.java,
                mapOf("reactortestpropagation.enabled" to "true", "micronaut.http.client.read-timeout" to "30s")
        )
        val client = embeddedServer.applicationContext.getBean(HttpClient::class.java)

        val result = Flux.range(1, 100)
                .flatMap {
                    val tracingId = UUID.randomUUID().toString()
                    val get = HttpRequest.POST<Any>("http://localhost:${embeddedServer.port}/trigger", NameRequestBody("sss-$tracingId"))
                            .header("X-TrackingId", tracingId)
                    Mono.from(client.retrieve(get, String::class.java))
                            .map { Tuples.of(it as String, tracingId) }
                }
                .collectList()
                .block()

        for (t in result!!) {
            assert(t.t1 == t.t2)
        }

        embeddedServer.stop()
    }
}

@Requires(property = "reactortestpropagation.enabled")
@Controller
class TestController(private val someService: SomeService) {

    @Post("/trigger")
    suspend fun trigger(request: HttpRequest<*>, @Body requestBody: SomeBody): String {
        return withContext(Dispatchers.IO) {
            someService.findValue()
        }
    }

    @Get("/data")
    suspend fun getTracingId(request: HttpRequest<*>): String {
        val reactorContextView = currentCoroutineContext()[ReactorContext.Key]!!.context
        return reactorContextView.get("reactorTrackingId") as String
    }
}

@Introspected
class SomeBody(val name: String)

@Requires(property = "reactortestpropagation.enabled")
@Singleton
class SomeService {

    suspend fun findValue(): String {
        delay(50)
        return withContext(Dispatchers.Default) {
            delay(50)
            val context = currentCoroutineContext()[ReactorContext.Key]!!.context
            val reactorTrackingId = context.get("reactorTrackingId") as String
            val suspendTrackingId = context.get("suspendTrackingId") as String
            if (reactorTrackingId != suspendTrackingId) {
                throw IllegalArgumentException()
            }
            suspendTrackingId
        }
    }
}

@Requires(property = "reactortestpropagation.enabled")
@Filter(MATCH_ALL_PATTERN)
class ReactorHttpServerFilter : HttpServerFilter {

    override fun doFilter(request: HttpRequest<*>, chain: ServerFilterChain): Publisher<MutableHttpResponse<*>> {
        val trackingId = request.headers["X-TrackingId"] as String
        return Mono.from(chain.proceed(request)).contextWrite {
            it.put("reactorTrackingId", trackingId)
        }
    }

    override fun getOrder(): Int = 1
}

@Requires(property = "reactortestpropagation.enabled")
@Filter(MATCH_ALL_PATTERN)
class SuspendHttpServerFilter : CoroutineHttpServerFilter {

    override suspend fun filter(request: HttpRequest<*>, chain: ServerFilterChain): MutableHttpResponse<*> {
        val trackingId = request.headers["X-TrackingId"] as String
        //withContext does not merge the current context so data may be lost
        return withContext(Context.of("suspendTrackingId", trackingId).asCoroutineContext()) {
            chain.next(request)
        }
    }

    override fun getOrder(): Int = 0
}

interface CoroutineHttpServerFilter : HttpServerFilter {

    suspend fun filter(request: HttpRequest<*>, chain: ServerFilterChain): MutableHttpResponse<*>

    override fun doFilter(request: HttpRequest<*>, chain: ServerFilterChain): Publisher<MutableHttpResponse<*>> {
        return mono {
            filter(request, chain)
        }
    }
}

suspend fun ServerFilterChain.next(request: HttpRequest<*>): MutableHttpResponse<*> {
    return proceed(request).asFlow().single()
}

@Introspected
class NameRequestBody(val name: String)
