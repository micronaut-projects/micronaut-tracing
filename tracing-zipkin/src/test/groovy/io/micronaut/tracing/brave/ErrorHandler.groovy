package io.micronaut.tracing.brave

import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Produces
import io.micronaut.http.server.exceptions.ExceptionHandler
import jakarta.inject.Singleton

@Produces
@Singleton
@Requires(property = 'spec.name', value = 'ErrorHandlerSpec')
class ErrorHandler implements ExceptionHandler<RuntimeException, HttpResponse> {

    List<RuntimeException> exceptions = []

    @Override
    HttpResponse handle(HttpRequest request, RuntimeException exception) {
        exceptions << exception
        return HttpResponse.badRequest()
    }
}
