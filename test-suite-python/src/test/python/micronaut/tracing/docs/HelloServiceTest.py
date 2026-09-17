from typing import Annotated

from jakarta.inject import Inject
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import BeforeEach, Disabled, Test

from .HelloService import HelloService

try:
    from io.opentelemetry.api import OpenTelemetry
    from io.opentelemetry.api.common import AttributeKey
    from io.opentelemetry.api.trace import SpanKind
    from io.opentelemetry.sdk.testing.exporter import InMemorySpanExporter
except ImportError:  # TODO(python): packages under `io.` other than `io.micronaut` cannot be imported at runtime
    from opentelemetry.api import OpenTelemetry
    from opentelemetry.api.common import AttributeKey
    from opentelemetry.api.trace import SpanKind
    from opentelemetry.sdk.testing.exporter import InMemorySpanExporter


@MicronautTest
class HelloServiceTest:

    hello_service: Annotated[HelloService, Inject]
    open_telemetry: Annotated[OpenTelemetry, Inject]
    exporter: Annotated[InMemorySpanExporter, Inject]

    @BeforeEach
    def reset_exporter(self) -> None:
        self.exporter.reset()

    @Test
    def new_span_creates_a_span_with_the_tagged_argument(self) -> None:
        assert self.hello_service.hello("Fred") == "Hello Fred"

        spans = self.exporter.getFinishedSpanItems()
        assert spans.size() == 1
        span = spans.get(0)
        assert span.getName() == "HelloService.hello#hello-world"
        assert span.getKind() == SpanKind.INTERNAL
        assert span.getAttributes().get(AttributeKey.stringKey("person.name")) == "Fred"

    # TODO(python): `self.greet(...)` inside `hello` invokes the Python method directly, not the intercepted
    # Java proxy of the bean, so the @ContinueSpan interceptor of the nested call never runs (in Java the
    # `this.greet(...)` call goes through the generated $Intercepted subclass)
    @Disabled("TODO(python): self-invocations of a Python bean bypass its interceptors (see DISABLED_TESTS.md)")
    @Test
    def continue_span_adds_the_tag_of_the_nested_call_to_the_span(self) -> None:
        self.hello_service.hello("Fred")

        span = self.exporter.getFinishedSpanItems().get(0)
        assert span.getAttributes().get(AttributeKey.stringKey("hello.greeting")) == "Hello Fred"

    @Test
    def continue_span_tags_the_current_span(self) -> None:
        outer = self.open_telemetry.getTracer("test").spanBuilder("outer").startSpan()
        scope = outer.makeCurrent()
        try:
            assert self.hello_service.greet("Hi") == "Hi"
        finally:
            scope.close()
            outer.end()

        spans = self.exporter.getFinishedSpanItems()
        assert spans.size() == 1
        assert spans.get(0).getName() == "outer"
        assert spans.get(0).getAttributes().get(AttributeKey.stringKey("hello.greeting")) == "Hi"
