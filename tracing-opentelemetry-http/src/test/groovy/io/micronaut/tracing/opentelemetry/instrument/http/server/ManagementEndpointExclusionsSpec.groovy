package io.micronaut.tracing.opentelemetry.instrument.http.server

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.management.endpoint.annotation.Endpoint
import io.micronaut.management.endpoint.annotation.Read
import io.micronaut.runtime.server.EmbeddedServer
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

class ManagementEndpointExclusionsSpec extends Specification {

    private static final String SPEC_NAME = "ManagementEndpointExclusionsSpec"

    ApplicationContext context
    EmbeddedServer server
    HttpClient client

    void cleanup() {
        client?.close()
        server?.stop()
        context?.close()
    }

    void "the management endpoints are not traced by default"() {
        given:
        start()

        expect:
        context.getBean(ManagementEndpointExclusions).patterns().containsAll(['/health(?:/.*)?', '/custom(?:/.*)?'])
        tracedPaths('/health', '/health/liveness', '/custom', '/healthy') == ['/healthy'] as Set
    }

    void "the management endpoints are traced when the exclusion is disabled"() {
        given:
        start('tracing.opentelemetry.http.server.exclude-management-endpoints': false)

        expect:
        context.getBean(ManagementEndpointExclusions).patterns().empty
        tracedPaths('/health', '/custom', '/healthy') == ['/health', '/custom', '/healthy'] as Set
    }

    void "a management endpoint can be traced again"() {
        given:
        start('tracing.opentelemetry.http.server.traced-management-endpoints': ['health'])

        expect:
        tracedPaths('/health', '/custom', '/healthy') == ['/health', '/healthy'] as Set
    }

    void "the management endpoints are excluded under their base path, with the configured exclusions"() {
        given:
        start('endpoints.all.path': '/manage', 'endpoints.custom.path': '/other', 'otel.exclusions[0]': '/healthy')

        expect:
        context.getBean(ManagementEndpointExclusions).patterns().containsAll(['/manage/health(?:/.*)?', '/manage/other(?:/.*)?'])
        tracedPaths('/manage/health', '/manage/other', '/healthy', '/hello') == ['/hello'] as Set
    }

    private void start(Map<String, Object> properties = [:]) {
        context = ApplicationContext.run([
            'spec.name'                       : SPEC_NAME,
            'micronaut.server.port'           : -1,
            'endpoints.health.sensitive'      : false,
            'endpoints.health.details-visible': 'ANONYMOUS'
        ] + properties)
        server = context.getBean(EmbeddedServer).start()
        client = context.createBean(HttpClient, server.URL)
    }

    /**
     * Calls the paths, then {@code /done}, and returns the paths of the server spans recorded once the span of
     * {@code /done} is recorded.
     */
    private Set<String> tracedPaths(String... paths) {
        for (String path : paths + ['/done']) {
            try {
                client.toBlocking().exchange(HttpRequest.GET(path), String)
            } catch (HttpClientResponseException ignored) {
                // e.g. a health check reporting DOWN
            }
        }
        def exporter = context.getBean(InMemorySpanExporter)
        Set<String> traced = null
        new PollingConditions(timeout: 10).eventually {
            traced = exporter.finishedSpanItems
                .findAll { it.kind == SpanKind.SERVER }
                .collect { it.attributes.asMap().find { k, v -> k.key == 'url.path' }?.value as String }
                .toSet()
            assert traced.contains('/done')
        }
        traced - '/done'
    }

    @Controller
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class TestController {

        @Get("/healthy")
        String healthy() {
            "healthy"
        }

        @Get("/hello")
        String hello() {
            "hello"
        }

        @Get("/done")
        String done() {
            "done"
        }
    }

    @Endpoint(id = "custom", defaultSensitive = false)
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class CustomEndpoint {

        @Read
        String read() {
            "custom"
        }
    }
}
