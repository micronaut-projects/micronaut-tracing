package io.micronaut.tracing.opentelemetry.instrument.http

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.runtime.server.EmbeddedServer
import io.opentelemetry.api.trace.SpanId
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import jakarta.inject.Inject
import reactor.core.publisher.Mono
import spock.lang.AutoCleanup
import spock.lang.Issue
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.time.Duration

/**
 * Reproduces https://github.com/micronaut-projects/micronaut-tracing/issues/475.
 *
 * A controller running on the event loop makes a span current and closes that scope on
 * another thread (after a reactor boundary). OpenTelemetry ignores the out-of-order scope
 * closes, so the controller's context (which contains the server span) stays current on the
 * event-loop thread. The next request handled by that thread must still get its own,
 * root server span.
 */
@Issue('https://github.com/micronaut-projects/micronaut-tracing/issues/475')
class OpenTelemetryLeakedContextSpec extends Specification {

    static final String SPEC_NAME = 'OpenTelemetryLeakedContextSpec'

    @AutoCleanup
    ApplicationContext context = ApplicationContext.run(
        'spec.name': SPEC_NAME,
        'otel.register.global': false,
        'micronaut.application.name': 'test-app',
        // a single event-loop thread guarantees both requests are handled by the same thread
        'micronaut.netty.event-loops.default.num-threads': 1
    )

    @AutoCleanup
    EmbeddedServer embeddedServer = context.getBean(EmbeddedServer).start()

    PollingConditions conditions = new PollingConditions()

    void 'span made current across a reactor boundary does not suppress following server spans'() {
        given:
        InMemorySpanExporter exporter = context.getBean(InMemorySpanExporter)

        when: 'the same endpoint is called sequentially, without any client side tracing'
        def bodies = (1..3).collect { new URL(embeddedServer.URL, '/leak/makeCurrent').text }

        then:
        bodies.every { it == 'ok' }

        and: 'every request has its own root server span which is the parent of the controller span'
        conditions.eventually {
            def serverSpans = exporter.finishedSpanItems.findAll { it.kind == SpanKind.SERVER }
            def childSpans = exporter.finishedSpanItems.findAll { it.kind == SpanKind.INTERNAL && it.name == 'findAllBooks' }

            assert childSpans.size() == 3
            assert serverSpans.size() == 3
            assert serverSpans*.name.every { it == 'GET /leak/makeCurrent' }
            assert serverSpans.every { it.parentSpanId == SpanId.invalid }
            assert serverSpans*.traceId.unique().size() == 3
            assert childSpans.every { child ->
                serverSpans.any { it.traceId == child.traceId && it.spanId == child.parentSpanId }
            }
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @Controller('/leak')
    static class LeakController {

        @Inject
        Tracer tracer

        /**
         * Same shape as the example application attached to issue #475.
         */
        @Get('/makeCurrent')
        Mono<String> makeCurrent() {
            def span = tracer.spanBuilder('findAllBooks').startSpan()
            def scope = span.makeCurrent()
            return Mono.delay(Duration.ofMillis(0))
                .map { 'ok' }
                .doOnNext {
                    scope.close()
                    span.end()
                }
        }
    }
}
