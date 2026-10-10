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
package io.micronaut.tracing.opentracing.interceptor;

import io.micronaut.aop.InterceptedMethod;
import io.micronaut.aop.InterceptorBean;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.async.propagation.ReactorPropagation;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.tracing.annotation.NewSpan;
import io.micronaut.tracing.opentracing.OpenTracingPropagationContext;
import io.micronaut.tracing.util.TracedMethod;
import io.micronaut.tracing.util.TracedMethodCache;
import io.opentracing.Span;
import io.opentracing.Tracer;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import reactor.core.CoreSubscriber;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Operators;
import reactor.util.context.Context;

import java.util.concurrent.CompletionStage;

/**
 * Implements tracing logic for {@code ContinueSpan} and {@code NewSpan}
 * using the Open Tracing API.
 *
 * @author graemerocher
 * @since 1.0
 * @deprecated OpenTracing is archived. Use Micronaut Tracing OpenTelemetry instead. See the
 * "Migrating from OpenTracing" section of the user guide.
 */
@Deprecated(since = "8.4.0", forRemoval = true)
@Internal
@Singleton
@Requires(beans = Tracer.class)
@InterceptorBean(value = NewSpan.class)
public final class NewSpanTraceInterceptor extends AbstractTraceInterceptor {

    private final TracedMethodCache<NewSpanMethod> methods = new TracedMethodCache<>(this::resolve);

    /**
     * Initialize the interceptor with tracer and conversion service.
     *
     * @param tracer            for span creation and propagation across arbitrary transports
     * @param conversionService the {@code ConversionService} instance
     */
    public NewSpanTraceInterceptor(Tracer tracer, ConversionService conversionService) {
        super(tracer, conversionService);
    }

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        NewSpanMethod method = methods.get(context);
        if (method.operationName == null) {
            return context.proceed();
        }

        Span currentSpan = tracer.activeSpan();
        Tracer.SpanBuilder builder = tracer.buildSpan(method.operationName);
        if (currentSpan != null) {
            builder.asChildOf(currentSpan);
        }

        Span span = builder.start();
        populateTags(span, method.className, method.tracedMethod, context.getParameterValues());

        return OpenTracingPropagationContext.withSpan(
                PropagatedContext.getOrEmpty(),
                tracer,
                span)
            .propagate(() -> interceptWithSpan(context, method, span));
    }

    private Object interceptWithSpan(MethodInvocationContext<Object, Object> context, NewSpanMethod method, Span span) {
        if (method.synchronous) {
            try {
                return context.proceed();
            } catch (Throwable e) {
                logError(span, e);
                throw e;
            } finally {
                span.finish();
            }
        }

        InterceptedMethod interceptedMethod = InterceptedMethod.of(context, conversionService);
        try {
            switch (interceptedMethod.resultType()) {
                case PUBLISHER -> {
                    // the propagated context with the span of the method, current while it is invoked
                    Publisher<?> publisher = new SpanPublisher(interceptedMethod.interceptResultAsPublisher(),
                        PropagatedContext.getOrEmpty());
                    // finish the span exactly once, on the terminal signal: completion, error or cancellation
                    if (method.single) {
                        return interceptedMethod.handleResult(
                            Mono.from(publisher)
                                .doOnError(throwable -> logError(span, throwable))
                                .doFinally(signal -> span.finish())
                        );
                    }
                    return interceptedMethod.handleResult(
                        Flux.from(publisher)
                            .doOnError(throwable -> logError(span, throwable))
                            .doFinally(signal -> span.finish())
                    );
                }
                case COMPLETION_STAGE -> {
                    CompletionStage<?> completionStage = interceptedMethod.interceptResultAsCompletionStage();
                    if (completionStage != null) {
                        completionStage = completionStage.whenComplete((o, throwable) -> {
                            if (throwable != null) {
                                logError(span, throwable);
                            }
                            span.finish();
                        });
                    }
                    return interceptedMethod.handleResult(completionStage);
                }
                case SYNCHRONOUS -> {
                    Object result = context.proceed();
                    span.finish();
                    return result;
                }
                default -> {
                    return interceptedMethod.unsupported();
                }
            }
        } catch (Exception e) {
            // thrown before the result could finish the span
            logError(span, e);
            span.finish();
            return interceptedMethod.handleException(e);
        }
    }

    private NewSpanMethod resolve(MethodInvocationContext<?, ?> context) {
        TracedMethod tracedMethod = TracedMethod.of(context);
        String className = context.getDeclaringType().getSimpleName();
        String operationName = null;
        if (tracedMethod.isNewSpan()) {
            operationName = tracedMethod.getNewSpanValue() != null
                ? tracedMethod.getNewSpanValue()
                : className + '.' + tracedMethod.getMethodName();
        }
        boolean synchronous = !context.isSuspend()
            && InterceptedMethod.of(context, conversionService).resultType() == InterceptedMethod.ResultType.SYNCHRONOUS;
        boolean single = Publishers.isSingle(context.getReturnType().getType());
        return new NewSpanMethod(tracedMethod, className, operationName, synchronous, single);
    }

    /**
     * The per-method data of the interceptor.
     *
     * @param tracedMethod  the span data of the method
     * @param className     the simple name of the declaring class
     * @param operationName the span name, {@code null} if the method is not a new span
     * @param synchronous   whether the method returns neither a reactive type nor a future
     * @param single        whether the reactive return type emits a single item
     */
    private record NewSpanMethod(TracedMethod tracedMethod,
                                 String className,
                                 String operationName,
                                 boolean synchronous,
                                 boolean single) {
    }

    /**
     * The publisher returned by a {@code NewSpan} method, in the context of the span of the method.
     *
     * <p>It is usually subscribed after the method returned, by the caller (e.g. with {@code Mono.toFuture()}
     * or by the HTTP server), with the context of the caller current. Its work is often started lazily, on
     * subscription (e.g. an HTTP client call in a {@code flatMap}), so it is subscribed with the propagated
     * context of the method current and in its Reactor context, which makes the span of the method the parent of
     * that work instead of the span of the caller. The signals are emitted to the subscriber in the propagated
     * context of the caller, so the code consuming them does not run with the span of the method active.</p>
     *
     * @param source            The publisher returned by the method
     * @param propagatedContext The propagated context of the method, with its span
     */
    private record SpanPublisher(Publisher<?> source, PropagatedContext propagatedContext) implements Publisher<Object> {

        @SuppressWarnings("unchecked")
        @Override
        public void subscribe(Subscriber<? super Object> subscriber) {
            CoreSubscriber<? super Object> actual = Operators.toCoreSubscriber(subscriber);
            Subscriber<Object> inContext = new CallerContextSubscriber(actual,
                ReactorPropagation.addPropagatedContext(actual.currentContext(), propagatedContext),
                PropagatedContext.getOrEmpty());
            propagatedContext.propagate(() -> ((Publisher<Object>) source).subscribe(inContext));
        }
    }

    /**
     * Emits the signals of the publisher of a {@code NewSpan} method to its subscriber in the propagated context
     * of the caller, and exposes the propagated context of the method to the publisher.
     *
     * @param actual        The subscriber
     * @param context       The Reactor context of the publisher, with the propagated context of the method
     * @param callerContext The propagated context of the caller, current when it subscribed
     */
    private record CallerContextSubscriber(CoreSubscriber<? super Object> actual,
                                           Context context,
                                           PropagatedContext callerContext) implements CoreSubscriber<Object> {

        @Override
        public Context currentContext() {
            return context;
        }

        @Override
        public void onSubscribe(Subscription subscription) {
            signal(() -> actual.onSubscribe(subscription));
        }

        @Override
        public void onNext(Object item) {
            signal(() -> actual.onNext(item));
        }

        @Override
        public void onError(Throwable throwable) {
            signal(() -> actual.onError(throwable));
        }

        @Override
        public void onComplete() {
            signal(actual::onComplete);
        }

        private void signal(Runnable signal) {
            if (callerContext.isBound()) {
                signal.run();
            } else {
                callerContext.propagate(signal);
            }
        }
    }
}
