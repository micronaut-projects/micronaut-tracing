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

import io.micronaut.tracing.opentelemetry.instrument.util.OpenTelemetryExclusionsConfiguration;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

/**
 * The per-request path exclusion check ({@code otel.exclusions}) used by the HTTP server and client
 * filters, with three patterns, for a path that is traced (no pattern matches, the common case) and one
 * that is excluded (the last pattern matches).
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
public class ExclusionBenchmark {

    public String traced = "/bench/hello/world";
    public String excluded = "/static/app.js";

    private Predicate<String> exclusionTest;

    @Setup
    public void setup() {
        OpenTelemetryExclusionsConfiguration configuration = new OpenTelemetryExclusionsConfiguration();
        configuration.setExclusions(List.of("/health.*", "/metrics.*", "/static/.*"));
        exclusionTest = configuration.exclusionTest();
    }

    @Benchmark
    public boolean tracedPath() {
        return exclusionTest.test(traced);
    }

    @Benchmark
    public boolean excludedPath() {
        return exclusionTest.test(excluded);
    }
}
