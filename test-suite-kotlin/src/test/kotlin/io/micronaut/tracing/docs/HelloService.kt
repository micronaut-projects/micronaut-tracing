package io.micronaut.tracing.docs

// tag::imports[]
import io.micronaut.tracing.annotation.ContinueSpan
import io.micronaut.tracing.annotation.NewSpan
import io.micronaut.tracing.annotation.SpanTag
import jakarta.inject.Singleton
// end::imports[]

// tag::clazz[]
@Singleton
open class HelloService {

    @NewSpan("hello-world") // <1>
    open fun hello(@SpanTag("person.name") name: String): String { // <2>
        return greet("Hello $name")
    }

    @ContinueSpan // <3>
    open fun greet(@SpanTag("hello.greeting") greet: String): String {
        return greet
    }
}
// end::clazz[]
