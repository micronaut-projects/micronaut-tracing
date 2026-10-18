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

import io.micronaut.context.ApplicationContext;
import io.micronaut.tracing.opentelemetry.interceptor.NewSpanOpenTelemetryTraceInterceptor;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

import java.util.concurrent.TimeUnit;

/**
 * Calls of {@code @NewSpan} methods returning each supported type, with no parent span (every call starts
 * a root span). The reactive results are consumed so the span is ended.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
public class NewSpanBenchmark {

    @Param({"NONE", "OTEL", "OTEL_NOOP_EXPORTER", "OTEL_RATIO_10"})
    public TracingMode mode;

    private ApplicationContext context;
    private TracedService service;

    @Setup(Level.Trial)
    public void setup() {
        context = ApplicationContext.builder()
            .deduceEnvironment(false)
            .properties(mode.properties())
            .start();
        boolean traced = context.containsBean(NewSpanOpenTelemetryTraceInterceptor.class);
        if (traced == (mode == TracingMode.NONE)) {
            throw new IllegalStateException("Unexpected interceptor presence " + traced + " for " + mode);
        }
        service = context.getBean(TracedService.class);
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        if (context != null) {
            context.close();
        }
    }

    @Benchmark
    public String sync() {
        return service.sync("v");
    }

    @Benchmark
    public String completionStage() {
        return service.completionStage("v").toCompletableFuture().join();
    }

    @Benchmark
    public String mono() {
        return service.mono("v").block();
    }

    @Benchmark
    public String flux() {
        return service.flux("v").blockLast();
    }
}
