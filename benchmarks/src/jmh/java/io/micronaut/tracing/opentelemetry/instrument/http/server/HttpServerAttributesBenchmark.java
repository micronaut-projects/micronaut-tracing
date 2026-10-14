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
package io.micronaut.tracing.opentelemetry.instrument.http.server;

import io.micronaut.context.ApplicationContext;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.tracing.benchmarks.TracingMode;
import io.micronaut.web.router.RouteAttributes;
import io.micronaut.web.router.Router;
import io.micronaut.web.router.UriRouteMatch;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

import java.util.concurrent.TimeUnit;

/**
 * The {@code http.route} lookup of {@link MicronautHttpServerAttributesGetter} for a request whose route
 * has been matched. It is in the getter's package because the getter is package-private. The instrumenter
 * resolves the route several times per request (span name, attributes, metrics), so multiply accordingly.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
public class HttpServerAttributesBenchmark {

    private ApplicationContext context;
    private HttpRequest<Object> request;

    @Setup(Level.Trial)
    @SuppressWarnings("unchecked")
    public void setup() {
        context = ApplicationContext.builder()
            .deduceEnvironment(false)
            .properties(TracingMode.NONE.properties())
            .start();
        MutableHttpRequest<Object> req = HttpRequest.GET("/bench/hello/world");
        UriRouteMatch<Object, Object> match = context.getBean(Router.class).findClosest(req);
        if (match == null) {
            throw new IllegalStateException("No route matched");
        }
        RouteAttributes.setRouteInfo(req, match.getRouteInfo());
        request = req;
        String route = httpRoute();
        if (!"/bench/hello/{name}".equals(route)) {
            throw new IllegalStateException("Unexpected route " + route);
        }
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        if (context != null) {
            context.close();
        }
    }

    @Benchmark
    public String httpRoute() {
        return MicronautHttpServerAttributesGetter.INSTANCE.getHttpRoute(request);
    }
}
