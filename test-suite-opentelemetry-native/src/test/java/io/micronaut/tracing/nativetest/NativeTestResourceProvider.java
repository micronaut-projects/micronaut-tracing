package io.micronaut.tracing.nativetest;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.ResourceProvider;
import io.opentelemetry.sdk.resources.Resource;

/**
 * An OpenTelemetry resource provider registered in {@code META-INF/services}, the way the resource
 * detectors of opentelemetry-java-contrib are, and configured through the Micronaut environment.
 */
public class NativeTestResourceProvider implements ResourceProvider {

    static final AttributeKey<String> REGION = AttributeKey.stringKey("native.region");

    @Override
    public Resource createResource(ConfigProperties config) {
        return Resource.create(Attributes.of(REGION, config.getString("native.region", "unknown")));
    }
}
