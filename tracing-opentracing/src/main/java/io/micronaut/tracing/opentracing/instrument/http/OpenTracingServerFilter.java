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
package io.micronaut.tracing.opentracing.instrument.http;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.filter.HttpServerFilter;
import io.micronaut.http.filter.ServerFilterChain;
import io.micronaut.tracing.opentracing.OpenTracingPropagationContext;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.Tracer.SpanBuilder;
import io.opentracing.noop.NoopTracer;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import static io.micronaut.http.filter.ServerFilterPhase.TRACING;
import static io.micronaut.tracing.opentracing.instrument.http.AbstractOpenTracingFilter.SERVER_PATH;
import static io.micronaut.tracing.opentracing.instrument.http.TraceRequestAttributes.CURRENT_SPAN;
import static io.micronaut.tracing.opentracing.instrument.http.TraceRequestAttributes.CURRENT_SPAN_CONTEXT;
import static io.opentracing.propagation.Format.Builtin.HTTP_HEADERS;

/**
 * An HTTP server instrumentation filter that uses Open Tracing.
 *
 * @author graemerocher
 * @since 1.0
 */
@Internal
@Filter(SERVER_PATH)
@Requires(beans = Tracer.class)
@Requires(missingBeans = NoopTracer.class)
public final class OpenTracingServerFilter extends AbstractOpenTracingFilter implements HttpServerFilter {

    /**
     * Creates an HTTP server instrumentation filter.
     *
     * @param tracer            for span creation and propagation across transport
     * @param conversionService the {@code ConversionService} instance
     * @param exclusionsConfig  The {@link TracingExclusionsConfiguration}
     */
    public OpenTracingServerFilter(Tracer tracer,
                                   ConversionService conversionService,
                                   @Nullable TracingExclusionsConfiguration exclusionsConfig) {
        super(tracer, conversionService, exclusionsConfig == null ? null : exclusionsConfig.exclusionTest());
    }

    @Override
    public Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
        if (shouldExclude(request.getPath())) {
            return chain.proceed(request);
        }

        Span currentSpan = tracer.activeSpan();
        SpanBuilder spanBuilder = newSpan(request, initSpanContext(request));
        if (currentSpan != null) {
            spanBuilder.asChildOf(currentSpan);
        }

        Span span = spanBuilder.start();
        span.setTag(TAG_HTTP_SERVER, true);
        request.setAttribute(CURRENT_SPAN_CONTEXT, span.context());
        request.setAttribute(CURRENT_SPAN, span);

        try (PropagatedContext.Scope ignore = PropagatedContext.getOrEmpty()
            .plus(new OpenTracingPropagationContext(tracer, span))
            .propagate()) {

            return Mono.from(chain.proceed(request))
                .doOnNext(response -> {
                    tracer.inject(span.context(), HTTP_HEADERS, new HttpHeadersTextMap(response.getHeaders()));
                    setResponseTags(request, response, span);
                })
                .doOnError(throwable -> setErrorTags(span, throwable))
                .doOnTerminate(span::finish);
        }
    }

    @Override
    public int getOrder() {
        return TRACING.order();
    }

    @NonNull
    private SpanContext initSpanContext(@NonNull HttpRequest<?> request) {
        SpanContext spanContext = tracer.extract(
            HTTP_HEADERS,
            new HttpHeadersTextMap(request.getHeaders())
        );
        request.setAttribute(CURRENT_SPAN_CONTEXT, spanContext);
        return spanContext;
    }

}
