package io.micronaut.tracing.docs;

import io.micronaut.context.annotation.Property;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.tracing.opentelemetry.ResourceProvider;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.resources.Resource;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

@Property(name = "spec.name", value = "AwsResourceProviderTest")
@MicronautTest(startApplication = false)
class AwsResourceProviderTest {

    @Inject
    ResourceProvider resourceProvider;

    @Test
    void theResourceProviderMergesTheAwsResourceIntoTheDefaultResource() {
        assertInstanceOf(AwsResourceProvider.class, resourceProvider);

        Resource resource = resourceProvider.resource();

        // the default resource attributes are kept when no EC2 metadata endpoint is reachable
        assertEquals("opentelemetry", resource.getAttribute(AttributeKey.stringKey("telemetry.sdk.name")));
        assertEquals("java", resource.getAttribute(AttributeKey.stringKey("telemetry.sdk.language")));
    }
}
