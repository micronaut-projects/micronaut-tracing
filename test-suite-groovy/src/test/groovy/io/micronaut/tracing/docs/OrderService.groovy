package io.micronaut.tracing.docs

// tag::imports[]
import io.opentelemetry.instrumentation.annotations.AddingSpanAttributes
import io.opentelemetry.instrumentation.annotations.SpanAttribute
import jakarta.inject.Singleton
// end::imports[]

// tag::clazz[]
@Singleton
class OrderService {

    @AddingSpanAttributes // <1>
    String process(@SpanAttribute("order.id") String orderId) { // <2>
        return "processed " + orderId
    }
}
// end::clazz[]
