/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.tracing.brave;

import brave.CurrentSpanCustomizer;
import brave.SpanCustomizer;
import brave.Tracing;
import brave.handler.SpanHandler;
import brave.opentracing.BraveTracer;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Nullable;
import io.opentracing.Tracer;
import io.opentracing.util.GlobalTracer;
import jakarta.inject.Singleton;
import zipkin2.Span;
import zipkin2.reporter.AsyncReporter;
import zipkin2.reporter.Reporter;
import zipkin2.reporter.brave.ZipkinSpanHandler;

import static zipkin2.reporter.Reporter.NOOP;

/**
 * Builds a <code>Tracer</code> for Brave using <code>BraveTracer</code>.
 *
 * @author graemerocher
 * @since 1.0
 */
@Factory
@Requires(beans = {BraveTracerConfiguration.class})
public class BraveTracerFactory {

    private final BraveTracerConfiguration configuration;

    /**
     * @param configuration the configuration
     */
    public BraveTracerFactory(BraveTracerConfiguration configuration) {
        this.configuration = configuration;
    }

    /**
     * The <code>Tracing</code> bean.
     *
     * @param reporter an optional <code>Reporter</code>
     * @return the <code>Tracing</code> bean
     */
    @Bean(preDestroy = "close")
    @Singleton
    @Requires(classes = Tracing.class)
    Tracing braveTracing(@Nullable Reporter<Span> reporter) {
        SpanHandler spanHandler = ZipkinSpanHandler.newBuilder(reporter == null ? NOOP : reporter).build();
        return configuration.getTracingBuilder()
                .addSpanHandler(spanHandler)
                .build();
    }

    /**
     * The <code>SpanCustomizer</code> bean.
     *
     * @param tracing the <code>Tracing</code> bean
     * @return the <code>SpanCustomizer</code> bean
     */
    @Singleton
    @Requires(beans = Tracing.class)
    @Requires(missingBeans = SpanCustomizer.class)
    SpanCustomizer spanCustomizer(Tracing tracing) {
        return CurrentSpanCustomizer.create(tracing);
    }

    /**
     * The Open Tracing <code>Tracer</code> bean.
     *
     * @param tracing the <code>Tracing</code> bean
     * @return the Open Tracing <code>Tracer</code> bean
     */
    @Singleton
    @Requires(classes = {BraveTracer.class, Tracer.class})
    @Primary
    Tracer braveTracer(Tracing tracing) {
        BraveTracer braveTracer = BraveTracer.create(tracing);
        GlobalTracer.registerIfAbsent(braveTracer);
        return braveTracer;
    }

    /**
     * A <code>Reporter</code> that is configured if no other is present and
     * <code>AsyncReporterConfiguration</code> is enabled.
     *
     * @param configuration the configuration
     * @return the <code>AsyncReporter</code> bean
     */
    @Prototype
    @Requires(beans = AsyncReporterConfiguration.class)
    @Requires(missingBeans = Reporter.class)
    AsyncReporter<Span> asyncReporter(AsyncReporterConfiguration configuration) {
        return configuration.getBuilder().build();
    }
}
