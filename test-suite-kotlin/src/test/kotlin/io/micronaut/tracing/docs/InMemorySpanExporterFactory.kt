package io.micronaut.tracing.docs

import io.micronaut.context.annotation.Factory
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SpanProcessor
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import jakarta.inject.Singleton

/**
 * Exports the finished spans of the tests to an in-memory exporter.
 */
@Factory
class InMemorySpanExporterFactory {

    @Singleton
    fun spanProcessor(spanExporter: InMemorySpanExporter): SpanProcessor = SimpleSpanProcessor.create(spanExporter)

    @Singleton
    fun inMemorySpanExporter(): InMemorySpanExporter = InMemorySpanExporter.create()
}
