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
import io.micronaut.core.convert.ConversionService;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.filter.ClientFilterChain;
import io.micronaut.http.filter.HttpClientFilter;
import io.micronaut.tracing.instrument.util.TracingObserver;
import io.micronaut.tracing.instrument.util.TracingPublisherUtils;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.Tracer.SpanBuilder;
import io.opentracing.noop.NoopTracer;
import jakarta.inject.Inject;
import org.reactivestreams.Publisher;

import static io.micronaut.tracing.instrument.http.AbstractOpenTracingFilter.CLIENT_PATH;
import static io.micronaut.tracing.instrument.http.TraceRequestAttributes.CURRENT_SPAN;
import static io.micronaut.tracing.instrument.http.TraceRequestAttributes.CURRENT_SPAN_CONTEXT;
import static io.opentracing.propagation.Format.Builtin.HTTP_HEADERS;

/**
 * An HTTP client instrumentation filter that uses Open Tracing.
 *
 * @author graemerocher
 * @since 1.0
 */
@Filter(CLIENT_PATH)
@Requires(beans = Tracer.class)
@Requires(missingBeans = NoopTracer.class)
public class OpenTracingClientFilter extends AbstractOpenTracingFilter implements HttpClientFilter {

    /**
     * @param tracer            the tracer for span creation and configuring across arbitrary transports
     * @param conversionService the {@code ConversionService} instance
     */
    public OpenTracingClientFilter(Tracer tracer, ConversionService conversionService) {
        this(tracer, conversionService, null);
    }

    /**
     * Initialize the open tracing client filter with tracer and exclusion configuration.
     *
     * @param tracer           the tracer for span creation and configuring across arbitrary transports
     * @param conversionService the {@code ConversionService} instance
     * @param exclusionsConfig The {@link TracingExclusionsConfiguration}
     */
    @Inject
    public OpenTracingClientFilter(Tracer tracer,
                                   ConversionService conversionService,
                                   @Nullable TracingExclusionsConfiguration exclusionsConfig) {
        super(tracer, conversionService, exclusionsConfig == null ? null : exclusionsConfig.exclusionTest());
    }

    @SuppressWarnings("unchecked")
    @Override
    public Publisher<? extends HttpResponse<?>> doFilter(MutableHttpRequest<?> request,
                                                         ClientFilterChain chain) {

        Publisher<? extends HttpResponse<?>> requestPublisher = chain.proceed(request);
        if (shouldExclude(request.getPath())) {
            return requestPublisher;
        }

        Span activeSpan = tracer.activeSpan();
        SpanContext activeContext = activeSpan == null ? null : activeSpan.context();
        SpanBuilder spanBuilder = newSpan(request, activeContext);

        return TracingPublisherUtils.createTracingPublisher(requestPublisher, tracer, spanBuilder, true, conversionService, new TracingObserver() {

            @Override
            public void doOnSubscribe(@NonNull Span span) {
                span.setTag(TAG_HTTP_CLIENT, true);
                SpanContext spanContext = span.context();
                tracer.inject(
                    spanContext,
                    HTTP_HEADERS,
                    new HttpHeadersTextMap(request.getHeaders())
                );
                request.setAttribute(CURRENT_SPAN_CONTEXT, spanContext);
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
                if (error instanceof HttpClientResponseException e) {
                    HttpResponse<?> response = e.getResponse();
                    setResponseTags(request, response, span);
                }
                setErrorTags(span, error);
            }
        });
    }
}
