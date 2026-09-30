package io.micronaut.tracing.docs

import io.micronaut.context.annotation.Property
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.tracing.opentelemetry.ResourceProvider
import io.opentelemetry.api.common.AttributeKey
import jakarta.inject.Inject
import spock.lang.Specification

@Property(name = "spec.name", value = "AwsResourceProviderSpec")
@MicronautTest(startApplication = false)
class AwsResourceProviderSpec extends Specification {

    @Inject
    ResourceProvider resourceProvider

    void "the resource provider merges the AWS resource into the default resource"() {
        expect:
        resourceProvider instanceof AwsResourceProvider

        when:
        def resource = resourceProvider.resource()

        then: "the default resource attributes are kept when no EC2 metadata endpoint is reachable"
        resource.getAttribute(AttributeKey.stringKey("telemetry.sdk.name")) == "opentelemetry"
        resource.getAttribute(AttributeKey.stringKey("telemetry.sdk.language")) == "java"
    }
}
