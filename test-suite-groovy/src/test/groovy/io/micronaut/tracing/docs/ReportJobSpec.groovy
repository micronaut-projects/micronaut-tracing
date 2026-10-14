package io.micronaut.tracing.docs

import io.micronaut.context.annotation.Property
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.data.SpanData
import jakarta.inject.Inject
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

@Property(name = "spec.name", value = "ReportJobSpec")
@Property(name = "tracing.opentelemetry.test.reset-before-each", value = "false") // the job runs at startup
@MicronautTest(startApplication = false)
class ReportJobSpec extends Specification {

    @Inject
    InMemorySpanExporter exporter

    void "each run of the scheduled method is a root span"() {
        when:
        new PollingConditions(timeout: 10).eventually {
            assert exporter.finishedSpanItems.any { it.name == "ReportJob.generate" }
        }
        SpanData span = exporter.finishedSpanItems.find { it.name == "ReportJob.generate" }

        then:
        span.kind == SpanKind.INTERNAL
        !span.parentSpanContext.valid
        span.attributes.get(AttributeKey.stringKey("code.function.name")) == ReportJob.name + ".generate"
        span.attributes.get(AttributeKey.stringKey("micronaut.scheduled.fixed_delay")) == "5m"
    }
}
