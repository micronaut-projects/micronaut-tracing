package io.micronaut.tracing.brave

import zipkin2.Span
import zipkin2.reporter.Reporter

/**
 * @author graemerocher
 * @since 1.0
 */
class TestReporter implements Reporter<Span> {

    final List<Span> spans = []

    @Override
    void report(Span span) {
        spans << span
    }
}
