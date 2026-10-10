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
package io.micronaut.tracing.opentelemetry.interceptor;

import io.micronaut.aop.InterceptedMethod;
import io.micronaut.aop.InterceptorBean;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.async.propagation.ReactorPropagation;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.tracing.annotation.NewSpan;
import io.micronaut.tracing.opentelemetry.OpenTelemetryPropagationContext;
import io.micronaut.tracing.util.TracedMethod;
import io.micronaut.tracing.util.TracedMethodCache;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.incubator.semconv.util.ClassAndMethod;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import reactor.core.CoreSubscriber;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Operators;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Implements tracing logic for {@code ContinueSpan} and {@code NewSpan}
 * using the Open Telemetry API.
 *
 * @author Nemanja Mikic
 * @since 4.2.0
 */
@Internal
@Singleton
@Requires(beans = Tracer.class)
@InterceptorBean(NewSpan.class)
public final class NewSpanOpenTelemetryTraceInterceptor extends AbstractOpenTelemetryTraceInterceptor {

    private final ConversionService conversionService;
    private final TracedMethodCache<NewSpanMethod> methods = new TracedMethodCache<>(this::resolve);

    /**
     * Initialize the interceptor with tracer and conversion service.
     *
     * @param instrumenter      The ClassAndMethod Instrumenter
     * @param conversionService The conversion service
     */
    public NewSpanOpenTelemetryTraceInterceptor(@Named("micronautCodeTelemetryInstrumenter") Instrumenter<ClassAndMethod, Object> instrumenter,
                                                ConversionService conversionService) {
        super(instrumenter);
        this.conversionService = conversionService;
    }

    @Nullable
    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        NewSpanMethod method = methods.get(context);
        ClassAndMethod classAndMethod = method.classAndMethod;
        if (classAndMethod == null) {
            return context.proceed();
        }
        Context currentContext = Context.current();
        // don't create a nested span if you're not supposed to.
        if (!instrumenter.shouldStart(currentContext, classAndMethod)) {
            return context.proceed();
        }
        if (method.synchronous) {
            return interceptSynchronous(context, method, currentContext);
        }

        InterceptedMethod interceptedMethod = InterceptedMethod.of(context, conversionService);
        final Context newContext = instrumenter.start(currentContext, classAndMethod);
        SpanEnd spanEnd = new SpanEnd(instrumenter, newContext, classAndMethod);
        PropagatedContext spanPropagatedContext = PropagatedContext.getOrEmpty()
            .plus(new OpenTelemetryPropagationContext(newContext));
        try (PropagatedContext.Scope ignore = spanPropagatedContext.propagate()) {

            tagArguments(Span.fromContext(newContext), method.tracedMethod, context.getParameterValues());

            switch (interceptedMethod.resultType()) {
                case PUBLISHER -> {
                    Publisher<?> publisher = new SpanPublisher(interceptedMethod.interceptResultAsPublisher(),
                        spanPropagatedContext);
                    // end the span exactly once, on the terminal signal: completion, error or cancellation
                    if (method.single) {
                        return interceptedMethod.handleResult(
                            Mono.from(publisher)
                                .doOnSuccess(spanEnd::success)
                                .doOnError(spanEnd)
                                .doOnCancel(spanEnd)
                        );
                    }
                    return interceptedMethod.handleResult(
                        Flux.from(publisher)
                            .doOnComplete(spanEnd)
                            .doOnError(spanEnd)
                            .doOnCancel(spanEnd)
                    );
                }
                case COMPLETION_STAGE -> {
                    CompletionStage<?> completionStage = interceptedMethod.interceptResultAsCompletionStage();
                    if (completionStage != null) {
                        completionStage = completionStage.whenComplete(spanEnd::end);
                    }
                    return interceptedMethod.handleResult(completionStage);
                }
                case SYNCHRONOUS -> {
                    Object response = context.proceed();
                    spanEnd.success(response);
                    return response;
                }
                default -> {
                    return interceptedMethod.unsupported();
                }
            }
        } catch (Exception e) {
            spanEnd.accept(e);
            return interceptedMethod.handleException(e);
        }
    }

    private Object interceptSynchronous(MethodInvocationContext<Object, Object> context,
                                        NewSpanMethod method,
                                        Context currentContext) {
        ClassAndMethod classAndMethod = method.classAndMethod;
        Context newContext = instrumenter.start(currentContext, classAndMethod);
        try (PropagatedContext.Scope ignore = PropagatedContext.getOrEmpty()
            .plus(new OpenTelemetryPropagationContext(newContext))
            .propagate()) {

            tagArguments(Span.fromContext(newContext), method.tracedMethod, context.getParameterValues());
            Object response = context.proceed();
            instrumenter.end(newContext, classAndMethod, response, null);
            return response;
        } catch (Throwable e) {
            instrumenter.end(newContext, classAndMethod, null, e);
            throw e;
        }
    }

    private NewSpanMethod resolve(MethodInvocationContext<?, ?> context) {
        TracedMethod tracedMethod = TracedMethod.of(context);
        if (!tracedMethod.isNewSpan()) {
            return new NewSpanMethod(tracedMethod, null, false, false);
        }
        String operationName = tracedMethod.getNewSpanValue();
        String methodName = operationName == null
            ? tracedMethod.getMethodName()
            : tracedMethod.getMethodName() + '#' + operationName;
        ClassAndMethod classAndMethod = ClassAndMethod.create(context.getDeclaringType(), methodName);
        boolean synchronous = !context.isSuspend()
            && InterceptedMethod.of(context, conversionService).resultType() == InterceptedMethod.ResultType.SYNCHRONOUS;
        boolean single = Publishers.isSingle(context.getReturnType().getType());
        return new NewSpanMethod(tracedMethod, classAndMethod, synchronous, single);
    }

    /**
     * The per-method data of the interceptor.
     *
     * @param tracedMethod   the span data of the method
     * @param classAndMethod the request of the instrumenter, {@code null} if the method is not a new span
     * @param synchronous    whether the method returns neither a reactive type nor a future
     * @param single         whether the reactive return type emits a single item
     */
    private record NewSpanMethod(TracedMethod tracedMethod,
                                 @Nullable ClassAndMethod classAndMethod,
                                 boolean synchronous,
                                 boolean single) {
    }

    /**
     * Ends the span once, whichever of the completion, error or cancellation signals comes first.
     */
    private static final class SpanEnd extends AtomicBoolean implements Runnable, Consumer<Throwable> {

        private final transient Instrumenter<ClassAndMethod, Object> instrumenter;
        private final transient Context context;
        private final transient ClassAndMethod classAndMethod;

        SpanEnd(Instrumenter<ClassAndMethod, Object> instrumenter, Context context, ClassAndMethod classAndMethod) {
            this.instrumenter = instrumenter;
            this.context = context;
            this.classAndMethod = classAndMethod;
        }

        void end(@Nullable Object response, @Nullable Throwable error) {
            if (compareAndSet(false, true)) {
                instrumenter.end(context, classAndMethod, response, error);
            }
        }

        void success(@Nullable Object response) {
            end(response, null);
        }

        /**
         * Completion without a value, or cancellation.
         */
        @Override
        public void run() {
            end(null, null);
        }

        @Override
        public void accept(Throwable throwable) {
            end(null, throwable);
        }
    }

    /**
     * The publisher returned by a {@code NewSpan} method, in the context of the span of the method.
     *
     * <p>It is usually subscribed after the method returned, by the caller (e.g. with {@code Mono.toFuture()}
     * or by the HTTP server), with the context of the caller current. Its work is often started lazily, on
     * subscription (e.g. an HTTP client call in a {@code flatMap}), so it is subscribed with the propagated
     * context of the method current and in its Reactor context, which makes the span of the method the parent of
     * that work instead of the span of the caller. The signals are emitted to the subscriber in the propagated
     * context of the caller, so the code consuming them does not run with the span of the method current.</p>
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
                                           reactor.util.context.Context context,
                                           PropagatedContext callerContext) implements CoreSubscriber<Object> {

        @Override
        public reactor.util.context.Context currentContext() {
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
