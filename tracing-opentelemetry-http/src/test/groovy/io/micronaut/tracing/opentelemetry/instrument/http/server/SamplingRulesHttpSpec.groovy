package io.micronaut.tracing.opentelemetry.instrument.http.server

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.runtime.server.EmbeddedServer
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

/**
 * The sampling rules see the {@code url.path} and {@code http.route} attributes of the server spans.
 */
class SamplingRulesHttpSpec extends Specification {

    private static final String SPEC_NAME = "SamplingRulesHttpSpec"

    @Shared
    @AutoCleanup
    ApplicationContext context = ApplicationContext.run([
        'spec.name'                                   : SPEC_NAME,
        'micronaut.server.port'                       : -1,
        'tracing.opentelemetry.sampler.rules[0].path' : '/rules/internal/.*',
        'tracing.opentelemetry.sampler.rules[1].route': '/rules/books/\\{id\\}'
    ])

    @Shared
    EmbeddedServer server = context.getBean(EmbeddedServer).start()

    void "root server spans matching a rule are dropped"() {
        when:
        ['/rules/internal/ping', '/rules/books/1', '/rules/books', '/rules/done'].each {
            // an untraced client: the server spans are roots
            new URL(server.URL, it).text
        }

        then:
        new PollingConditions(timeout: 10).eventually {
            assert serverSpanPaths().contains('/rules/done')
        }
        serverSpanPaths() == ['/rules/books', '/rules/done'] as Set
    }

    private Set<String> serverSpanPaths() {
        context.getBean(InMemorySpanExporter).finishedSpanItems
            .findAll { it.kind == SpanKind.SERVER }
            .collect { it.attributes.asMap().find { k, v -> k.key == 'url.path' }?.value as String }
            .toSet()
    }

    @Controller("/rules")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class RulesController {

        @Get("/internal/ping")
        String ping() {
            "pong"
        }

        @Get("/books/{id}")
        String book(String id) {
            id
        }

        @Get("/books")
        String books() {
            "books"
        }

        @Get("/done")
        String done() {
            "done"
        }
    }
}
