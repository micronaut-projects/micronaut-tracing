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
package io.micronaut.tracing.instrument.http;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.filter.HttpServerFilter;
import io.micronaut.http.filter.ServerFilterChain;
import io.micronaut.tracing.instrument.util.TracingPublisher;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.Tracer.SpanBuilder;
import io.opentracing.noop.NoopTracer;
import jakarta.inject.Inject;
import org.reactivestreams.Publisher;

import static io.micronaut.http.filter.ServerFilterPhase.TRACING;
import static io.micronaut.tracing.instrument.http.AbstractOpenTracingFilter.SERVER_PATH;
import static io.micronaut.tracing.instrument.http.TraceRequestAttributes.CURRENT_SPAN;
import static io.micronaut.tracing.instrument.http.TraceRequestAttributes.CURRENT_SPAN_CONTEXT;
import static io.opentracing.propagation.Format.Builtin.HTTP_HEADERS;

/**
 * An HTTP server instrumentation filter that uses Open Tracing.
 *
 * @author graemerocher
 * @since 1.0
 */
@Filter(SERVER_PATH)
@Requires(beans = Tracer.class)
@Requires(missingBeans = NoopTracer.class)
public class OpenTracingServerFilter extends AbstractOpenTracingFilter implements HttpServerFilter {

    private static final String APPLIED = OpenTracingServerFilter.class.getName() + "-applied";
    private static final String CONTINUE = OpenTracingServerFilter.class.getName() + "-continue";

    /**
     * Creates an HTTP server instrumentation filter.
     *
     * @param tracer for span creation and propagation across transport
     */
    public OpenTracingServerFilter(Tracer tracer) {
        this(tracer, null);
    }

    /**
     * Creates an HTTP server instrumentation filter.
     *
     * @param tracer           for span creation and propagation across transport
     * @param exclusionsConfig The {@link TracingExclusionsConfiguration}
     */
    @Inject
    public OpenTracingServerFilter(Tracer tracer,
                                   @Nullable TracingExclusionsConfiguration exclusionsConfig) {
        super(tracer, exclusionsConfig == null ? null : exclusionsConfig.exclusionTest());
    }

    @SuppressWarnings("unchecked")
    @Override
    public Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
        boolean applied = request.getAttribute(APPLIED, Boolean.class).orElse(false);
        boolean continued = request.getAttribute(CONTINUE, Boolean.class).orElse(false);
        if ((applied && !continued) || shouldExclude(request.getPath())) {
            return chain.proceed(request);
        }

        SpanBuilder spanBuilder = continued ? null : newSpan(request, initSpanContext(request));
        return new TracingPublisher(chain.proceed(request), tracer, spanBuilder) {

            @Override
            protected void doOnSubscribe(@NonNull Span span) {
                span.setTag(TAG_HTTP_SERVER, true);
                request.setAttribute(CURRENT_SPAN, span);
            }

            @Override
            protected void doOnNext(@NonNull Object object, @NonNull Span span) {
                if (!(object instanceof HttpResponse)) {
                    return;
                }

                HttpResponse<?> response = (HttpResponse<?>) object;
                tracer.inject(span.context(), HTTP_HEADERS, new HttpHeadersTextMap(response.getHeaders()));
                setResponseTags(request, response, span);
            }

            @Override
            protected void doOnError(@NonNull Throwable throwable, @NonNull Span span) {
                request.setAttribute(CONTINUE, true);
                setErrorTags(span, throwable);
            }

            @Override
            protected boolean isContinued() {
                return continued;
            }

            @Override
            protected boolean isFinishOnError() {
                return false;
            }
        };
    }

    @Override
    public int getOrder() {
        return TRACING.order();
    }

    @NonNull
    private SpanContext initSpanContext(@NonNull HttpRequest<?> request) {
        request.setAttribute(APPLIED, true);
        SpanContext spanContext = tracer.extract(
                HTTP_HEADERS,
                new HttpHeadersTextMap(request.getHeaders())
        );
        request.setAttribute(CURRENT_SPAN_CONTEXT, spanContext);
        return spanContext;
    }
}
