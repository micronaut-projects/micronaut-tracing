package io.micronaut.tracing.opentelemetry.test

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.opentelemetry.api.OpenTelemetry
import jakarta.inject.Inject
import spock.lang.Specification
import spock.lang.Stepwise

@Stepwise
@MicronautTest(startApplication = false)
class ResetSpansBeforeEachSpec extends Specification {

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

    void "second test starts with no spans"() {
        expect:
        spans.finishedSpans().empty
    }
}
