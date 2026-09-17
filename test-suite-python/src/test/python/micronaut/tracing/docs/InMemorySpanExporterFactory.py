from jakarta.inject import Singleton
from micronaut.context.annotation import Factory

try:
    from io.opentelemetry.sdk.testing.exporter import InMemorySpanExporter
    from io.opentelemetry.sdk.trace import SpanProcessor
    from io.opentelemetry.sdk.trace.export import SimpleSpanProcessor
except ImportError:  # TODO(python): packages under `io.` other than `io.micronaut` cannot be imported at runtime
    from opentelemetry.sdk.testing.exporter import InMemorySpanExporter
    from opentelemetry.sdk.trace import SpanProcessor
    from opentelemetry.sdk.trace.export import SimpleSpanProcessor


@Factory
class InMemorySpanExporterFactory:
    """Exports the finished spans of the tests to an in-memory exporter."""

    @Singleton
    def span_processor(self, span_exporter: InMemorySpanExporter) -> SpanProcessor:
        return SimpleSpanProcessor.create(span_exporter)

    @Singleton
    def in_memory_span_exporter(self) -> InMemorySpanExporter:
        return InMemorySpanExporter.create()
