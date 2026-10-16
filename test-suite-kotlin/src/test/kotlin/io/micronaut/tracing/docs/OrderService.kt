package io.micronaut.tracing.docs

// tag::imports[]
import io.opentelemetry.instrumentation.annotations.AddingSpanAttributes
import io.opentelemetry.instrumentation.annotations.SpanAttribute
import jakarta.inject.Singleton
// end::imports[]

// tag::clazz[]
@Singleton
open class OrderService {

    @AddingSpanAttributes // <1>
    open fun process(@SpanAttribute("order.id") orderId: String): String { // <2>
        return "processed $orderId"
    }
}
// end::clazz[]
