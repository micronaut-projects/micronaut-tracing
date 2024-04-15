package io.micronaut.tracing.opentelementry.xray.test;

import io.micronaut.context.annotation.Property;
import io.micronaut.core.convert.format.MapFormat;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.stream.Collectors;

import static io.micronaut.core.convert.format.MapFormat.MapTransformation.FLAT;
import static org.junit.jupiter.api.Assertions.assertEquals;

@MicronautTest(startApplication = false)
class OpenTelemetryExclusionsConfigurationTest {

    @Test
    void possibleToRetrieveConfiguration(@Property(name = "otel") @MapFormat(transformation = FLAT) Map<String, String> otelConfig) {
        Map<String, String> otel = otelConfig.entrySet().stream().collect(Collectors.toMap(
                e -> "otel." + e.getKey(),
                Map.Entry::getValue
        ));
        assertEquals("/health", otel.get("otel.traces.exclusions"));
        assertEquals("tracecontext, baggage, xray", otel.get("otel.traces.propagator"));
        assertEquals("otlp", otel.get("otel.traces.exporter"));

    }
}
