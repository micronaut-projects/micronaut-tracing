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
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.tracing.opentelemetry.instrument.http.server.OpenTelemetryServerFilter;
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

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.TimeUnit;

/**
 * A GET request through the full Micronaut HTTP server filter chain (embedded Netty server), sent by the
 * JDK {@link HttpClient} over a kept-alive HTTP/1.1 connection. The client is not instrumented, so the
 * difference between the modes is the server-side tracing overhead. {@code -prof gc} reports the
 * allocations of every thread of the JVM (client and server event loop), so the client cost is a constant
 * offset across the modes.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
public class HttpServerBenchmark {

    @Param({"NONE", "OTEL", "OTEL_NOOP_EXPORTER", "OTEL_RATIO_10"})
    public TracingMode mode;

    private ApplicationContext context;
    private HttpClient client;
    private HttpRequest request;

    @Setup(Level.Trial)
    public void setup() throws IOException, InterruptedException {
        context = ApplicationContext.builder()
            .deduceEnvironment(false)
            .properties(mode.properties())
            .start();
        EmbeddedServer server = context.getBean(EmbeddedServer.class).start();
        boolean traced = context.containsBean(OpenTelemetryServerFilter.class);
        if (traced == (mode == TracingMode.NONE)) {
            throw new IllegalStateException("Unexpected tracing filter presence " + traced + " for " + mode);
        }
        client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
        request = HttpRequest.newBuilder(URI.create(server.getURL() + "/bench/hello/world")).GET().build();
        String body = request();
        if (!"Hello world".equals(body)) {
            throw new IllegalStateException("Unexpected response: " + body);
        }
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        if (client != null) {
            client.close();
        }
        if (context != null) {
            context.close();
        }
    }

    @Benchmark
    public String request() throws IOException, InterruptedException {
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("Unexpected status " + response.statusCode());
        }
        return response.body();
    }
}
