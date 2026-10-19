package io.micronaut.tracing.nativetest;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.ResourceProvider;
import io.opentelemetry.sdk.resources.Resource;

/**
 * An OpenTelemetry resource provider registered in {@code META-INF/services} that the tests disable with
 * {@code otel.java.disabled.resource.providers}.
 */
public class DisabledResourceProvider implements ResourceProvider {

    static final AttributeKey<String> DISABLED = AttributeKey.stringKey("native.disabled");

    @Override
    public Resource createResource(ConfigProperties config) {
        return Resource.create(Attributes.of(DISABLED, "true"));
    }
}
