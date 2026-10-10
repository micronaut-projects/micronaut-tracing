package io.micronaut.tracing.nativetest;

import io.micronaut.context.annotation.Property;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.tracing.opentelemetry.test.TestSpans;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * OpenTelemetry autoconfigure reads its properties from the Micronaut environment through
 * {@code EnvironmentConfigProperties}, installed with the internal {@code AutoConfigureUtil} API (a reflective
 * call, covered by the reachability metadata of micronaut-tracing-opentelemetry), and loads its SPI
 * implementations (resource and propagator providers) with the {@link java.util.ServiceLoader}.
 */
@MicronautTest
@Property(name = "micronaut.application.name", value = "native-tests")
@Property(name = "otel.resource.attributes.deployment.environment.name", value = "native")
@Property(name = "otel.propagators", value = "tracecontext,baggage,b3multi")
@Property(name = "otel.span.attribute.count.limit", value = "64")
@Property(name = "native.region", value = "eu-west-1")
@Property(name = "otel.java.disabled.resource.providers", value = "io.micronaut.tracing.nativetest.DisabledResourceProvider")
class EnvironmentConfigPropertiesTest {

    @Inject
    OpenTelemetry openTelemetry;

    @Inject
    TestSpans spans;

    @Test
    void resourceIsConfiguredFromTheEnvironment() {
        openTelemetry.getTracer("native-test").spanBuilder("configured").startSpan().end();

        Resource resource = spans.awaitSpans(1).get(0).getResource();
        assertEquals("native-tests", resource.getAttribute(AttributeKey.stringKey("service.name")));
        assertEquals("native", resource.getAttribute(AttributeKey.stringKey("deployment.environment.name")));
        assertEquals("eu-west-1", resource.getAttribute(NativeTestResourceProvider.REGION));
        assertNull(resource.getAttribute(DisabledResourceProvider.DISABLED));
    }

    @Test
    void propagatorsAreLoadedThroughTheServiceLoader() {
        Collection<String> fields = openTelemetry.getPropagators().getTextMapPropagator().fields();

        assertTrue(fields.contains("traceparent"), () -> "Propagator fields: " + fields);
        assertTrue(fields.contains("baggage"), () -> "Propagator fields: " + fields);
        assertTrue(fields.contains("X-B3-TraceId"), () -> "Propagator fields: " + fields);
    }

    @Test
    void spanLimitsAreConfiguredFromTheEnvironment() {
        OpenTelemetrySdk sdk = assertInstanceOf(OpenTelemetrySdk.class, openTelemetry);

        assertEquals(64, sdk.getSdkTracerProvider().getSpanLimits().getMaxNumberOfAttributes());
    }
}
