package io.micronaut.tracing.docs

import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import jakarta.inject.Inject
import spock.lang.Specification

@Property(name = "spec.name", value = "OrderedTracingServerFilterSpec")
@MicronautTest
class OrderedTracingServerFilterSpec extends Specification {

    @Inject
    @Client("/")
    HttpClient client

    @Inject
    InMemorySpanExporter exporter

    void setup() {
        exporter.reset()
    }

    void "the filter tags the current server span"() {
        when:
        String body = client.toBlocking().retrieve("/api/ordered")
        def serverSpans = exporter.finishedSpanItems.findAll { it.kind == SpanKind.SERVER }

        then:
        body == "ordered"
        serverSpans.size() == 1
        serverSpans[0].name == "GET /api/ordered"
        serverSpans[0].attributes.get(AttributeKey.stringKey("example.attribute")) == "value"
        serverSpans[0].attributes.get(AttributeKey.stringKey("example.response.attribute")) == "value"
    }

    @Requires(property = "spec.name", value = "OrderedTracingServerFilterSpec")
    @Controller("/api")
    static class OrderedController {

        @Get("/ordered")
        String ordered() {
            return "ordered"
        }
    }
}
