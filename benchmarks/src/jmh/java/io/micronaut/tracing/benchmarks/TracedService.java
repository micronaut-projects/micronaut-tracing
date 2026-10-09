/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.tracing.benchmarks;

import io.micronaut.tracing.annotation.NewSpan;
import io.micronaut.tracing.annotation.SpanTag;
import jakarta.inject.Singleton;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * {@code @NewSpan} methods for each supported return type, used by {@link NewSpanBenchmark}. The span
 * names are left to their defaults so the runtime name resolution is part of the measurement.
 */
@Singleton
public class TracedService {

    /**
     * @param value a tagged argument
     * @return the value
     */
    @NewSpan
    public String sync(@SpanTag("bench.value") String value) {
        return value;
    }

    /**
     * @param value a tagged argument
     * @return a completed stage
     */
    @NewSpan
    public CompletionStage<String> completionStage(@SpanTag("bench.value") String value) {
        return CompletableFuture.completedFuture(value);
    }

    /**
     * @param value a tagged argument
     * @return a single element Mono
     */
    @NewSpan
    public Mono<String> mono(@SpanTag("bench.value") String value) {
        return Mono.just(value);
    }

    /**
     * @param value a tagged argument
     * @return a three element Flux
     */
    @NewSpan
    public Flux<String> flux(@SpanTag("bench.value") String value) {
        return Flux.just(value, value, value);
    }
}
