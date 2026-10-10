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
package io.micronaut.tracing.opentelemetry.instrument.scheduling;

import io.micronaut.aop.InterceptPhase;
import io.micronaut.aop.InterceptorBean;
import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.core.util.StringUtils;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.scheduling.ScheduledExecution;
import io.micronaut.scheduling.annotation.Scheduled;
import io.micronaut.tracing.opentelemetry.OpenTelemetryPropagationContext;
import io.micronaut.tracing.opentelemetry.annotation.ScheduledSpan;
import io.micronaut.tracing.util.TracedMethod;
import io.micronaut.tracing.util.TracedMethodCache;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.instrumentation.api.incubator.semconv.code.CodeSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.util.ClassAndMethod;
import io.opentelemetry.semconv.CodeAttributes;
import jakarta.inject.Singleton;

import java.util.Arrays;

/**
 * Traces the runs of the {@code @Scheduled} methods: each invocation of a method by the scheduler is an
 * {@code INTERNAL} span named after the method ({@code ClassName.method}), the root span of a new trace
 * whatever the context of the scheduler thread. The span has the {@code code.function.name} attribute and the
 * schedule that triggered the run ({@code micronaut.scheduled.cron}, {@code micronaut.scheduled.fixed_delay} or
 * {@code micronaut.scheduled.fixed_rate}), and records the exception of a failed run.
 *
 * <p>The interceptor is bound by {@link ScheduledSpan}, which the {@code micronaut-tracing-opentelemetry-annotation}
 * processor adds to the scheduled methods. A direct call of a scheduled method by the application is not traced:
 * the interceptor only starts a span when the {@link ScheduledExecution} of the current propagated context is the
 * invocation of the intercepted method.</p>
 *
 * <p>Disabled with {@code tracing.opentelemetry.scheduled.enabled=false}.</p>
 *
 * @author Graeme Rocher
 * @since 8.4.0
 */
@Internal
@Singleton
@Requires(beans = OpenTelemetry.class)
@Requires(property = OpenTelemetryScheduledConfig.ENABLED, notEquals = StringUtils.FALSE)
@InterceptorBean(ScheduledSpan.class)
public final class ScheduledSpanInterceptor implements MethodInterceptor<Object, Object> {

    /**
     * The {@code micronaut.scheduled.cron} attribute: the cron expression of the schedule.
     */
    public static final AttributeKey<String> CRON = AttributeKey.stringKey("micronaut.scheduled.cron");

    /**
     * The {@code micronaut.scheduled.fixed_delay} attribute: the fixed delay of the schedule.
     */
    public static final AttributeKey<String> FIXED_DELAY = AttributeKey.stringKey("micronaut.scheduled.fixed_delay");

    /**
     * The {@code micronaut.scheduled.fixed_rate} attribute: the fixed rate of the schedule.
     */
    public static final AttributeKey<String> FIXED_RATE = AttributeKey.stringKey("micronaut.scheduled.fixed_rate");

    private static final String INSTRUMENTATION_NAME = "io.micronaut.scheduling";

    /**
     * The scheduled invocation traced by the span of the context, so that a nested invocation of the same
     * method within the run does not start a second span.
     */
    private static final ContextKey<ScheduledExecution> EXECUTION = ContextKey.named("micronaut-scheduled-execution");

    private static final SpanNameExtractor<ClassAndMethod> NAMES =
        CodeSpanNameExtractor.create(ClassAndMethod.codeAttributesGetter());

    private final Instrumenter<ScheduledRun, Object> instrumenter;
    private final TracedMethodCache<ScheduledMethod> methods = new TracedMethodCache<>(ScheduledSpanInterceptor::resolve);

    /**
     * @param openTelemetry the OpenTelemetry instance
     */
    public ScheduledSpanInterceptor(OpenTelemetry openTelemetry) {
        this.instrumenter = Instrumenter.<ScheduledRun, Object>builder(openTelemetry, INSTRUMENTATION_NAME,
                run -> run.method().spanName())
            .addAttributesExtractor(new ScheduledAttributesExtractor())
            .buildInstrumenter(SpanKindExtractor.alwaysInternal());
    }

    @Override
    public int getOrder() {
        // outside of the other tracing interceptors of the method, such as @NewSpan
        return InterceptPhase.TRACE.getPosition() - 1;
    }

    @Override
    public @Nullable Object intercept(MethodInvocationContext<Object, Object> context) {
        ScheduledExecution execution = ScheduledExecution.current().orElse(null);
        if (execution == null
            || !isInvocationOf(execution.method(), context)
            || Context.current().get(EXECUTION) == execution) {
            return context.proceed();
        }
        ScheduledRun run = new ScheduledRun(methods.get(context), execution.schedule());
        // a run is the root of a new trace, whatever the context of the scheduler thread
        Context parentContext = Context.root();
        if (!instrumenter.shouldStart(parentContext, run)) {
            return context.proceed();
        }
        Context spanContext = instrumenter.start(parentContext, run).with(EXECUTION, execution);
        PropagatedContext propagatedContext = PropagatedContext.getOrEmpty()
            .plus(new OpenTelemetryPropagationContext(spanContext));
        try {
            // the callback form works in both the thread-local and the scoped-value propagation modes
            Object result = propagatedContext.propagate(() -> context.proceed());
            instrumenter.end(spanContext, run, result, null);
            return result;
        } catch (Throwable e) {
            instrumenter.end(spanContext, run, null, e);
            throw e;
        }
    }

    private static boolean isInvocationOf(ExecutableMethod<?, ?> scheduled, MethodInvocationContext<Object, Object> context) {
        ExecutableMethod<Object, Object> intercepted = context.getExecutableMethod();
        if (scheduled == intercepted) {
            return true;
        }
        return scheduled.getMethodName().equals(intercepted.getMethodName())
            && scheduled.getDeclaringType().isAssignableFrom(intercepted.getDeclaringType())
            && Arrays.equals(scheduled.getArgumentTypes(), intercepted.getArgumentTypes());
    }

    private static ScheduledMethod resolve(MethodInvocationContext<?, ?> context) {
        Class<?> declaringType = context.getDeclaringType();
        String methodName = TracedMethod.methodName(context.getExecutableMethod());
        String spanName = NAMES.extract(ClassAndMethod.create(declaringType, methodName));
        return new ScheduledMethod(spanName, declaringType.getName() + '.' + methodName);
    }

    /**
     * The per-method data of the interceptor.
     *
     * @param spanName     the span name
     * @param functionName the fully qualified name of the method, the {@code code.function.name} attribute
     */
    record ScheduledMethod(String spanName, String functionName) {
    }

    /**
     * A run of a scheduled method, the request of the instrumenter.
     *
     * @param method   the scheduled method
     * @param schedule the schedule that triggered the run
     */
    record ScheduledRun(ScheduledMethod method, AnnotationValue<Scheduled> schedule) {
    }

    /**
     * Adds the code and schedule attributes.
     */
    private static final class ScheduledAttributesExtractor implements AttributesExtractor<ScheduledRun, Object> {

        @Override
        public void onStart(AttributesBuilder attributes, Context parentContext, ScheduledRun run) {
            attributes.put(CodeAttributes.CODE_FUNCTION_NAME, run.method().functionName());
            AnnotationValue<Scheduled> schedule = run.schedule();
            schedule.stringValue("cron").filter(StringUtils::isNotEmpty)
                .ifPresent(cron -> attributes.put(CRON, cron));
            schedule.stringValue("fixedDelay").filter(StringUtils::isNotEmpty)
                .ifPresent(delay -> attributes.put(FIXED_DELAY, delay));
            schedule.stringValue("fixedRate").filter(StringUtils::isNotEmpty)
                .ifPresent(rate -> attributes.put(FIXED_RATE, rate));
        }

        @Override
        public void onEnd(AttributesBuilder attributes,
                          Context context,
                          ScheduledRun run,
                          @Nullable Object response,
                          @Nullable Throwable error) {
            // the attributes are only added on start
        }
    }
}
