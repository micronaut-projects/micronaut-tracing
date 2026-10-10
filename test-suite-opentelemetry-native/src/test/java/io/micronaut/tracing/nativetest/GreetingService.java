package io.micronaut.tracing.nativetest;

import io.micronaut.tracing.annotation.ContinueSpan;
import io.micronaut.tracing.annotation.NewSpan;
import io.micronaut.tracing.annotation.SpanTag;
import io.opentelemetry.instrumentation.annotations.SpanAttribute;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import jakarta.inject.Singleton;

/**
 * Traced methods whose span names and tags come from the metadata computed at compile time.
 */
@Singleton
public class GreetingService {

    @NewSpan
    public String greet(@SpanTag("greeting.name") String name) {
        return "Hello " + name;
    }

    @NewSpan("custom-greeting")
    public String customGreet(String name) {
        return "Hi " + name;
    }

    @ContinueSpan
    public String continueGreet(@SpanTag("greeting.continued") String name) {
        return "Hey " + name;
    }

    @WithSpan("otel-greeting")
    public String otelGreet(@SpanAttribute("greeting.otel") String name) {
        return "Hello again " + name;
    }
}
