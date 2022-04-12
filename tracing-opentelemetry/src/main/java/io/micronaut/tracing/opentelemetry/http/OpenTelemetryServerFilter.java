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
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpHeaders;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.filter.HttpServerFilter;
import io.micronaut.http.filter.ServerFilterChain;
import io.micronaut.tracing.opentelemetry.util.TracingObserver;
import io.micronaut.tracing.opentelemetry.util.TracingPublisherUtils;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapSetter;
import jakarta.inject.Inject;
import org.reactivestreams.Publisher;

import static io.micronaut.http.filter.ServerFilterPhase.TRACING;
import static io.micronaut.tracing.opentelemetry.http.AbstractOpenTracingFilter.SERVER_PATH;
import static io.micronaut.tracing.opentelemetry.http.TraceRequestAttributes.CURRENT_SPAN;
import static io.micronaut.tracing.opentelemetry.http.TraceRequestAttributes.CURRENT_SPAN_CONTEXT;

/**
 * An HTTP server instrumentation filter that uses Open Telemetry.
 *
 * @author Nemanja Mikic
 * @since 1.0
 */
@Filter(SERVER_PATH)
@Requires(beans = Tracer.class)
public class OpenTelemetryServerFilter extends AbstractOpenTracingFilter implements HttpServerFilter {

    private static final String APPLIED = OpenTelemetryServerFilter.class.getName() + "-applied";
    private static final String CONTINUE = OpenTelemetryServerFilter.class.getName() + "-continue";

    /**
     * Creates an HTTP server instrumentation filter.
     *
     * @param openTelemetry the openTelemetry
     * @param tracer for span creation and propagation across transport
     */
    public OpenTelemetryServerFilter(OpenTelemetry openTelemetry, Tracer tracer) {
        this(openTelemetry, tracer, null);
    }

    /**
     * Creates an HTTP server instrumentation filter.
     *
     * @param openTelemetry the openTelemetry
     * @param tracer           for span creation and propagation across transport
     * @param exclusionsConfig The {@link TracingExclusionsConfiguration}
     */
    @Inject
    public OpenTelemetryServerFilter(OpenTelemetry openTelemetry, Tracer tracer,
                                     @Nullable TracingExclusionsConfiguration exclusionsConfig) {
        super(openTelemetry, tracer, exclusionsConfig == null ? null : exclusionsConfig.exclusionTest());
    }

    @SuppressWarnings("unchecked")
    @Override
    public Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
        boolean applied = request.getAttribute(APPLIED, Boolean.class).orElse(false);
        boolean continued = request.getAttribute(CONTINUE, Boolean.class).orElse(false);
        if ((applied && !continued) || shouldExclude(request.getPath())) {
            return chain.proceed(request);
        }
        initSpanContext(request);
        SpanBuilder spanBuilder = continued ? null : newSpan(request);
        return TracingPublisherUtils.createTracingPublisher(chain.proceed(request), spanBuilder, new TracingObserver() {

            @Override
            public void doOnSubscribe(@NonNull Span span) {
                span.setAttribute(TAG_HTTP_SERVER, true);
                request.setAttribute(CURRENT_SPAN, span);
            }

            @Override
            public void doOnNext(@NonNull Object object, @NonNull Span span) {
                if (!(object instanceof HttpResponse)) {
                    return;
                }

                HttpResponse<?> response = (HttpResponse<?>) object;

                TextMapSetter<HttpHeaders> setter =
                    (carrier, key, value) -> {
                        if (carrier instanceof MutableHttpHeaders) {
                            ((MutableHttpHeaders) carrier).set(key, value);
                        }
                    };

                openTelemetry.getPropagators().getTextMapPropagator().inject(Context.current(), response.getHeaders(), setter);
                setResponseTags(request, response, span);
            }

            @Override
            public void doOnError(@NonNull Throwable throwable, @NonNull Span span) {
                request.setAttribute(CONTINUE, true);
                setErrorTags(span, throwable);
            }

            @Override
            public boolean isContinued() {
                return continued;
            }

            @Override
            public boolean isFinishOnError() {
                return false;
            }
        });
    }

    @Override
    public int getOrder() {
        return TRACING.order();
    }

    @NonNull
    private Context initSpanContext(@NonNull HttpRequest<?> request) {
        request.setAttribute(APPLIED, true);

        TextMapGetter<HttpRequest> getter =
            new TextMapGetter<HttpRequest>() {
                @Override
                public String get(HttpRequest carrier, String key) {
                    if (carrier.getHeaders().asMap().containsKey(key)) {
                        return carrier.getHeaders().asMap().get(key).get(0);
                    }
                    return null;
                }

                @Override
                public Iterable<String> keys(HttpRequest carrier) {
                    return carrier.getHeaders().asMap().keySet();
                }
            };

        Context extractedContext = openTelemetry.getPropagators().getTextMapPropagator()
            .extract(Context.current(), request, getter);

        request.setAttribute(CURRENT_SPAN_CONTEXT, extractedContext);
        extractedContext.makeCurrent();
        return extractedContext;
    }
}
