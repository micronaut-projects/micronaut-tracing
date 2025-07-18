package io.micronaut.tracing.opentracing.instrument.http

import io.jaegertracing.internal.reporters.InMemoryReporter
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClient
import io.micronaut.http.exceptions.HttpStatusException
import io.micronaut.runtime.server.EmbeddedServer
import io.opentracing.Scope
import io.opentracing.Tracer
import spock.lang.Issue
import spock.lang.Specification

import java.util.concurrent.CompletableFuture

class OpenTracingClientFilterSpec extends Specification {
    @Issue('https://github.com/micronaut-projects/micronaut-tracing/issues/784')
    def 'subscribe after proceed'() {
        given:
        def ctx = ApplicationContext.builder([
                'spec.name': 'OpenTracingClientFilterSpec',
                'tracing.jaeger.enabled': true,
                'tracing.jaeger.sampler.probability': 1
        ]).singletons(new InMemoryReporter()).start()
        def server = ctx.getBean(EmbeddedServer)
        server.start()
        def tracer = ctx.getBean(Tracer)

        def client = ctx.createBean(HttpClient, server.URI).toBlocking()
        def futures = new ArrayList<CompletableFuture<?>>()
        for (int thread = 0; thread < 4; thread++) {
            futures.add(CompletableFuture.runAsync {
                try (Scope _ = tracer.activateSpan(tracer.buildSpan("foo").start().setBaggageItem("foo", "foo"))) {
                    for (int i = 0; i < 10000; i++) {
                        client.retrieve("/check-baggage")
                    }
                }
            })
        }
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).get()

        cleanup:
        ctx.close()
    }

    @Controller
    @Requires(property = "spec.name", value = "OpenTracingClientFilterSpec")
    static class MyController {
        @Get("/check-baggage")
        String checkBaggage(HttpRequest<?> request) {
            if (request.headers.get("uberctx-foo") != "foo") {
                throw new HttpStatusException(HttpStatus.BAD_REQUEST, "tracing baggage missing")
            }
            return "ok"
        }
    }
}
