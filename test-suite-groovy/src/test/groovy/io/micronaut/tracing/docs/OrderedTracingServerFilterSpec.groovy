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
import spock.util.concurrent.PollingConditions

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

    void "the filter records to the server span in request and response filters"() {
        given:
        def conditions = new PollingConditions(timeout: 5)

        when:
        String body = client.toBlocking().retrieve("/api/hello")

        then:
        body == "Hello"

        and: "the server span is finished after the response filters ran, which may happen after the client got the response"
        conditions.eventually {
            def serverSpan = exporter.finishedSpanItems.find { it.kind == SpanKind.SERVER }
            serverSpan != null
            serverSpan.name == "GET /api/hello"
            serverSpan.attributes.get(AttributeKey.stringKey("example.attribute")) == "value"
            serverSpan.attributes.get(AttributeKey.stringKey("example.response.attribute")) == "value"
        }
    }

    @Controller("/api")
    @Requires(property = "spec.name", value = "OrderedTracingServerFilterSpec")
    static class HelloController {

        @Get("/hello")
        String hello() {
            "Hello"
        }
    }
}
