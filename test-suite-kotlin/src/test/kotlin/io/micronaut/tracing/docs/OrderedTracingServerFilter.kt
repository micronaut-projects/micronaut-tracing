package io.micronaut.tracing.docs

// tag::imports[]
import io.micronaut.core.order.Ordered
import io.micronaut.http.HttpRequest
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.annotation.RequestFilter
import io.micronaut.http.annotation.ResponseFilter
import io.micronaut.http.annotation.ServerFilter
import io.micronaut.http.filter.ServerFilterPhase.TRACING
import io.opentelemetry.api.trace.Span
// end::imports[]

// tag::filter[]
@ServerFilter("/api/**")
class OrderedTracingServerFilter : Ordered {

    @RequestFilter
    fun traceRequest(request: HttpRequest<*>) {
        Span.current().setAttribute("example.attribute", "value") // <1>
    }

    @ResponseFilter
    fun traceResponse(request: HttpRequest<*>, response: MutableHttpResponse<*>) {
        Span.current().setAttribute("example.response.attribute", "value") // <2>
    }

    override fun getOrder(): Int = TRACING.after() // <3>
}
// end::filter[]
