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
package io.micronaut.tracing.opentelemetry.http;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpHeaders;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.filter.ClientFilterChain;
import io.micronaut.http.filter.HttpClientFilter;
import io.micronaut.tracing.opentelemetry.util.TracingObserver;
import io.micronaut.tracing.opentelemetry.util.TracingPublisherUtils;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapSetter;
import jakarta.inject.Inject;
import org.reactivestreams.Publisher;

import static io.micronaut.tracing.opentelemetry.http.AbstractOpenTracingFilter.CLIENT_PATH;
import static io.micronaut.tracing.opentelemetry.http.TraceRequestAttributes.CURRENT_SPAN;
import static io.micronaut.tracing.opentelemetry.http.TraceRequestAttributes.CURRENT_SPAN_CONTEXT;

/**
 * An HTTP client instrumentation filter that uses Open Telemetry.
 *
 * @author Nemanja Mikic
 * @since 1.0
 */
@Filter(CLIENT_PATH)
@Requires(beans = Tracer.class)
//@Requires(missingBeans = NoopTracer.class)
public class OpenTracingClientFilter extends AbstractOpenTracingFilter implements HttpClientFilter {

    /**
     * @param tracer the tracer for span creation and configuring across arbitrary transports
     */
    public OpenTracingClientFilter(OpenTelemetry openTelemetry, Tracer tracer) {
        this(openTelemetry, tracer, null);
    }

    /**
     * Initialize the open tracing client filter with tracer and exclusion configuration.
     *
     * @param tracer           the tracer for span creation and configuring across arbitrary transports
     * @param exclusionsConfig The {@link TracingExclusionsConfiguration}
     */
    @Inject
    public OpenTracingClientFilter(OpenTelemetry openTelemetry, Tracer tracer,
                                   @Nullable TracingExclusionsConfiguration exclusionsConfig) {
        super(openTelemetry, tracer, exclusionsConfig == null ? null : exclusionsConfig.exclusionTest());
    }

    @SuppressWarnings("unchecked")
    @Override
    public Publisher<? extends HttpResponse<?>> doFilter(MutableHttpRequest<?> request,
                                                         ClientFilterChain chain) {

        Publisher<? extends HttpResponse<?>> requestPublisher = chain.proceed(request);
        if (shouldExclude(request.getPath())) {
            return requestPublisher;
        }

        SpanBuilder spanBuilder = newSpan(request);

        return TracingPublisherUtils.createTracingPublisher(requestPublisher, tracer, spanBuilder, true, new TracingObserver() {

            @Override
            public void doOnSubscribe(@NonNull Span span) {
                TextMapSetter<MutableHttpHeaders> setter =
                    (carrier, key, value) -> {
                        // Insert the context as Header
                        carrier.set(key, value);
                    };


                span.setAttribute(TAG_HTTP_CLIENT, true);
                openTelemetry.getPropagators().getTextMapPropagator().inject(
                    Context.current(), request.getHeaders(), setter
                );
                request.setAttribute(CURRENT_SPAN_CONTEXT, Context.current());
                request.setAttribute(CURRENT_SPAN, span);
            }

            @Override
            public void doOnNext(@NonNull Object object, @NonNull Span span) {
                if (object instanceof HttpResponse) {
                    setResponseTags(request, (HttpResponse<?>) object, span);
                }
            }

            @Override
            public void doOnError(@NonNull Throwable error, @NonNull Span span) {
                if (error instanceof HttpClientResponseException) {
                    HttpClientResponseException e = (HttpClientResponseException) error;
                    HttpResponse<?> response = e.getResponse();
                    setResponseTags(request, response, span);
                }
                setErrorTags(span, error);
            }
        });
    }
}
