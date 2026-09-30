# tag::imports[]
from typing import Annotated

from jakarta.inject import Singleton
from micronaut.tracing.annotation import ContinueSpan, NewSpan, SpanTag
# end::imports[]


# tag::clazz[]
@Singleton
class HelloService:

    @NewSpan("hello-world")  # <1>
    def hello(self, name: Annotated[str, SpanTag("person.name")]) -> str:  # <2>
        return self.greet("Hello " + name)

    @ContinueSpan  # <3>
    def greet(self, greet: Annotated[str, SpanTag("hello.greeting")]) -> str:
        return greet
# end::clazz[]
