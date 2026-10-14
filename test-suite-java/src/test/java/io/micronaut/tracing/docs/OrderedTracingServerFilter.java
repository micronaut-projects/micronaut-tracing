package io.micronaut.tracing.docs;

import io.micronaut.context.annotation.Requires;

// tag::imports[]
import io.micronaut.core.order.Ordered;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ResponseFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.opentelemetry.api.trace.Span;

import static io.micronaut.http.filter.ServerFilterPhase.TRACING;
// end::imports[]

@Requires(property = "spec.name", value = "OrderedTracingServerFilterTest")
// tag::clazz[]
@ServerFilter("/api/**")
class OrderedTracingServerFilter implements Ordered {

    @RequestFilter
    void traceRequest(HttpRequest<?> request) {
        Span.current().setAttribute("example.attribute", "value"); // <1>
    }

    @ResponseFilter
    void traceResponse(HttpRequest<?> request, MutableHttpResponse<?> response) {
        Span.current().setAttribute("example.response.attribute", "value");
    }

    @Override
    public int getOrder() {
        return TRACING.after(); // <2>
    }
}
// end::clazz[]
