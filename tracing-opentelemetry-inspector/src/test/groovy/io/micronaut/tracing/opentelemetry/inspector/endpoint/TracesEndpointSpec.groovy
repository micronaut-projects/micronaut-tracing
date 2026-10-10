package io.micronaut.tracing.opentelemetry.inspector.endpoint

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.BlockingHttpClient
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.tracing.opentelemetry.inspector.InspectedTrace
import io.micronaut.tracing.opentelemetry.inspector.TraceSummary
import spock.lang.AutoCleanup
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

class TracesEndpointSpec extends Specification {

    @AutoCleanup
    EmbeddedServer server

    @AutoCleanup
    HttpClient httpClient

    BlockingHttpClient client

    PollingConditions conditions = new PollingConditions(timeout: 10)

    void start(Map<String, Object> properties) {
        server = ApplicationContext.run(EmbeddedServer, ['spec.name': 'TracesEndpointSpec'] + properties)
        // a client outside the application context, so that requests are not traced as client spans
        httpClient = HttpClient.create(server.URL)
        client = httpClient.toBlocking()
    }

    void "the traces endpoint lists and returns traces"() {
        given:
        start('tracing.opentelemetry.inspector.enabled': true, 'endpoints.traces.sensitive': false)

        when:
        client.exchange("/inspected/hello/Fred", String)
        client.exchange("/inspected/fail")

        then:
        thrown(HttpClientResponseException)

        and:
        conditions.eventually {
            assert summaries("/traces?name=inspected")*.httpRoute() == ["/inspected/fail", "/inspected/hello/{name}"]
        }

        when:
        List<TraceSummary> hello = summaries("/traces?name=hello&status=200")
        List<TraceSummary> errors = summaries("/traces?name=inspected&error=true")

        then:
        hello.size() == 1
        hello[0].httpMethod() == "GET"
        hello[0].httpStatus() == 200
        hello[0].urlPath() == "/inspected/hello/Fred"
        hello[0].name() == "GET /inspected/hello/{name}"
        errors*.httpStatus() == [500]
        summaries("/traces?name=inspected&limit=1")*.httpRoute() == ["/inspected/fail"]
        summaries("/traces?name=inspected&minDuration=1h").isEmpty()
        summaries("/traces?name=inspected&since=2000-01-01T00:00:00Z").size() == 2

        when:
        InspectedTrace trace = client.retrieve(HttpRequest.GET("/traces/${errors[0].traceId()}"), InspectedTrace)

        then:
        trace.summary().traceId() == errors[0].traceId()
        trace.spans()[0].kind() == "SERVER"
        trace.spans()[0].status() == "ERROR"
        trace.spans()[0].attributes()["http.response.status_code"] == 500
        trace.spans()[0].events()*.name().contains("exception")

        when:
        client.exchange("/traces/00000000000000000000000000000001", String)

        then:
        HttpClientResponseException notFound = thrown()
        notFound.status == HttpStatus.NOT_FOUND

        when:
        HttpResponse<?> cleared = client.exchange(HttpRequest.DELETE("/traces"))

        then:
        cleared.status().code < 300
        summaries("/traces?name=inspected").isEmpty()
    }

    void "the traces endpoint is sensitive by default"() {
        given:
        start('tracing.opentelemetry.inspector.enabled': true)

        when:
        client.exchange("/traces", String)

        then:
        HttpClientResponseException e = thrown()
        e.status == HttpStatus.UNAUTHORIZED
    }

    void "the traces endpoint is not available when the inspector is disabled"() {
        given:
        start('endpoints.traces.sensitive': false)

        when:
        client.exchange("/traces", String)

        then:
        HttpClientResponseException e = thrown()
        e.status == HttpStatus.NOT_FOUND
    }

    private List<TraceSummary> summaries(String uri) {
        client.retrieve(HttpRequest.GET(uri), Argument.listOf(TraceSummary))
    }

    @Controller("/inspected")
    @Requires(property = "spec.name", value = "TracesEndpointSpec")
    static class InspectedController {

        @Get("/hello/{name}")
        String hello(String name) {
            "Hello $name"
        }

        @Get("/fail")
        String fail() {
            throw new IllegalStateException("boom")
        }
    }
}
