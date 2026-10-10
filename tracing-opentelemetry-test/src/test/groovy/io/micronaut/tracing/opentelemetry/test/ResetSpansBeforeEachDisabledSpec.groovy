package io.micronaut.tracing.opentelemetry.test

import io.micronaut.context.annotation.Property
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.opentelemetry.api.OpenTelemetry
import jakarta.inject.Inject
import spock.lang.Specification
import spock.lang.Stepwise

@Stepwise
@MicronautTest(startApplication = false)
@Property(name = "tracing.opentelemetry.test.reset-before-each", value = "false")
class ResetSpansBeforeEachDisabledSpec extends Specification {

    @Inject
    OpenTelemetry openTelemetry

    @Inject
    TestSpans spans

    void "first test records a span"() {
        when:
        openTelemetry.getTracer("test").spanBuilder("first").startSpan().end()

        then:
        spans.finishedSpans()*.name == ["first"]
    }

    void "second test still sees the span"() {
        expect:
        spans.finishedSpans()*.name == ["first"]
    }
}
