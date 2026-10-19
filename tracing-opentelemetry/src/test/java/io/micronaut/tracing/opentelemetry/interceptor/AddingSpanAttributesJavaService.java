package io.micronaut.tracing.opentelemetry.interceptor;

import io.micronaut.context.annotation.Requires;
import io.opentelemetry.instrumentation.annotations.AddingSpanAttributes;
import io.opentelemetry.instrumentation.annotations.SpanAttribute;
import jakarta.inject.Singleton;

@Requires(property = "spec.name", value = "AddingSpanAttributesSpec")
@Singleton
public class AddingSpanAttributesJavaService {

    @AddingSpanAttributes
    public String add(@SpanAttribute("java.attribute") String value, String notAnAttribute, @SpanAttribute String named) {
        return value + notAnAttribute + named;
    }
}
