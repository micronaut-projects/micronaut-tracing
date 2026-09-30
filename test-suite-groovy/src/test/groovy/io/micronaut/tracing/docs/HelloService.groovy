package io.micronaut.tracing.docs

// tag::imports[]
import io.micronaut.tracing.annotation.ContinueSpan
import io.micronaut.tracing.annotation.NewSpan
import io.micronaut.tracing.annotation.SpanTag
import jakarta.inject.Singleton
// end::imports[]

// tag::clazz[]
@Singleton
class HelloService {

    @NewSpan("hello-world") // <1>
    String hello(@SpanTag("person.name") String name) { // <2>
        return greet("Hello " + name)
    }

    @ContinueSpan // <3>
    String greet(@SpanTag("hello.greeting") String greet) {
        return greet
    }
}
// end::clazz[]
