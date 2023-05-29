package opentelemetry;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.annotation.*;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.tracing.annotation.ContinueSpan;
import io.micronaut.tracing.annotation.NewSpan;
import io.micronaut.tracing.annotation.SpanTag;
import io.opentelemetry.instrumentation.annotations.SpanAttribute;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import jakarta.inject.Inject;
import reactor.core.publisher.Mono;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;

import static io.micronaut.scheduling.TaskExecutors.IO;

@Controller("/annotations")
public class TracedController {

        @Inject
        @Client("/")
        ReactorHttpClient reactorHttpClient;

        @ExecuteOn(IO)
        @Post("/enter")
        @NewSpan("enter")
        Mono<String> enter(@Header("X-TrackingId") String tracingId, @Body SomeBody body) {
            return Mono.from(
                reactorHttpClient.retrieve(HttpRequest
                    .GET("/annotations/test")
                    .header("X-TrackingId", tracingId), String.class)
            );
        }

        @ExecuteOn(IO)
        @Get("/test")
        @ContinueSpan
        Mono<String> test(@SpanAttribute("tracing-annotation-span-attribute")
                          @Header("X-TrackingId") String tracingId) {
            privateMethodTest(tracingId);
            return Mono.from(
                reactorHttpClient.retrieve(HttpRequest
                    .GET("/annotations/test2")
                    .header("X-TrackingId", tracingId), String.class)
            );
        }

        @ContinueSpan
        void privateMethodTest(@SpanAttribute("privateMethodTestAttribute") String traceId) {

        }

        @ExecuteOn(IO)
        @Get("/test2")
        Mono<String> test2(@SpanTag("tracing-annotation-span-tag-no-withspan")
                           @Header("X-TrackingId") String tracingId) throws ExecutionException, InterruptedException {
            methodWithSpan(tracingId).toCompletableFuture().get();
            return Mono.just(tracingId);
        }

        @WithSpan("test-withspan-mapping")
        CompletionStage<Void> methodWithSpan(@SpanTag("tracing-annotation-span-tag-with-withspan") String tracingId) {
            return CompletableFuture.runAsync(() -> {
                String s = normalFunctionWithNewSpan(tracingId);
            });
        }

        @NewSpan
        String normalFunctionWithNewSpan(String tracingId) {
            return tracingId;
        }
}
