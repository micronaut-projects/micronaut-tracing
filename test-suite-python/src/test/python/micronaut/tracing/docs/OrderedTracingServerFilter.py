from micronaut.context.annotation import Requires

# tag::imports[]
from io.opentelemetry.api.trace import Span
from micronaut.core.order import Ordered
from micronaut.http import HttpRequest, MutableHttpResponse
from micronaut.http.annotation import RequestFilter, ResponseFilter, ServerFilter
from micronaut.http.filter import ServerFilterPhase
# end::imports[]


@Requires(property="spec.name", value="OrderedTracingServerFilterTest")
# tag::clazz[]
@ServerFilter("/api/**")
class OrderedTracingServerFilter(Ordered):

    @RequestFilter
    def trace_request(self, request: HttpRequest) -> None:
        Span.current().setAttribute("example.attribute", "value")  # <1>

    @ResponseFilter
    def trace_response(self, request: HttpRequest, response: MutableHttpResponse) -> None:
        Span.current().setAttribute("example.response.attribute", "value")

    def getOrder(self) -> int:
        return ServerFilterPhase.TRACING.after()  # <2>
# end::clazz[]
