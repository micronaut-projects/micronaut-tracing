package io.micronaut.tracing.opentelemetry.instrument.http.server;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;

@Requires(property = "spec.name", value = "HttpServerCodeAttributesSpec")
@Controller("/code-java")
public class CodeAttributesJavaController {

    @Get("/books/{id}")
    public String book(@PathVariable String id) {
        return "book " + id;
    }
}
