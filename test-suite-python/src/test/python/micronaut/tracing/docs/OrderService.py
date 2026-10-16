# tag::imports[]
from typing import Annotated

from io.opentelemetry.instrumentation.annotations import AddingSpanAttributes, SpanAttribute
from jakarta.inject import Singleton
# end::imports[]


# tag::clazz[]
@Singleton
class OrderService:

    @AddingSpanAttributes  # <1>
    def process(self, order_id: Annotated[str, SpanAttribute("order.id")]) -> str:  # <2>
        return "processed " + order_id
# end::clazz[]
