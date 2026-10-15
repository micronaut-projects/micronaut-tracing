package io.micronaut.tracing.opentelemetry.interceptor;

import io.micronaut.context.annotation.Requires;
import io.micronaut.tracing.annotation.NewSpan;
import jakarta.inject.Singleton;

@Requires(property = "spec.name", value = "NewSpanExpressionSpec")
@Singleton
public class ExpressionSpanService {

    @NewSpan("#{ 'call-' + #name }")
    public String call(String name) {
        return name;
    }
}
