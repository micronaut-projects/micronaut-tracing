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
package io.micronaut.tracing.opentelemetry.log;

import io.micronaut.core.annotation.Internal;
import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import org.jspecify.annotations.Nullable;
import org.slf4j.MDC;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Copies the trace context of an OpenTelemetry {@link Context} into the SLF4J {@link MDC} while that context
 * is made current by Micronaut's context propagation, and restores the previous MDC values afterwards.
 *
 * <p>The active correlation is held statically, like the propagation mode of Micronaut's
 * {@link io.micronaut.core.propagation.PropagatedContext}, because the propagated context elements are
 * created by the instrumentation without access to the bean context. It is installed by
 * {@link OpenTelemetryMdcInstaller} on startup and removed when the application context is closed; when several
 * application contexts run, the correlation of the most recently started one that is still running is active.</p>
 *
 * @since 8.4.0
 */
@Internal
public final class MdcTraceCorrelation {

    private static final int TRACE_ID = 0;
    private static final int SPAN_ID = 1;
    private static final int TRACE_FLAGS = 2;
    private static final int FIRST_BAGGAGE = 3;

    /**
     * The correlations of the running application contexts, in installation order. Guarded by the class lock.
     */
    private static final List<MdcTraceCorrelation> INSTALLED = new ArrayList<>();

    /**
     * The active correlation, {@code null} when none is installed.
     */
    private static final AtomicReference<MdcTraceCorrelation> CURRENT = new AtomicReference<>();

    private final String[] keys;

    MdcTraceCorrelation(String traceIdKey, String spanIdKey, String traceFlagsKey, List<String> baggageKeys) {
        keys = new String[FIRST_BAGGAGE + baggageKeys.size()];
        keys[TRACE_ID] = traceIdKey;
        keys[SPAN_ID] = spanIdKey;
        keys[TRACE_FLAGS] = traceFlagsKey;
        for (int i = 0; i < baggageKeys.size(); i++) {
            keys[FIRST_BAGGAGE + i] = baggageKeys.get(i);
        }
    }

    /**
     * Makes the context current and, when the MDC correlation is installed, copies its trace context into
     * the MDC. Closing the returned scope closes the OpenTelemetry scope and restores the previous MDC values.
     *
     * @param context The context
     * @return the scope
     */
    public static Scope makeCurrent(Context context) {
        Scope scope = context.makeCurrent();
        MdcTraceCorrelation correlation = CURRENT.get();
        return correlation == null ? scope : correlation.correlate(context, scope);
    }

    /**
     * @return the installed correlation, if any
     */
    @Nullable
    static MdcTraceCorrelation current() {
        return CURRENT.get();
    }

    /**
     * Installs the correlation of an application context. It is the active one until it is uninstalled or the
     * correlation of another application context is installed.
     *
     * @param correlation The correlation
     */
    static synchronized void install(MdcTraceCorrelation correlation) {
        INSTALLED.add(correlation);
        CURRENT.set(correlation);
    }

    /**
     * Uninstalls the correlation of an application context. The most recently installed correlation of the
     * application contexts still running becomes the active one.
     *
     * @param correlation The correlation
     */
    static synchronized void uninstall(MdcTraceCorrelation correlation) {
        INSTALLED.remove(correlation);
        CURRENT.set(INSTALLED.isEmpty() ? null : INSTALLED.get(INSTALLED.size() - 1));
    }

    private Scope correlate(Context context, Scope scope) {
        String[] values = new String[keys.length];
        SpanContext spanContext = Span.fromContext(context).getSpanContext();
        if (spanContext.isValid()) {
            values[TRACE_ID] = spanContext.getTraceId();
            values[SPAN_ID] = spanContext.getSpanId();
            values[TRACE_FLAGS] = spanContext.getTraceFlags().asHex();
        }
        if (keys.length > FIRST_BAGGAGE) {
            Baggage baggage = Baggage.fromContext(context);
            for (int i = FIRST_BAGGAGE; i < keys.length; i++) {
                values[i] = baggage.getEntryValue(keys[i]);
            }
        }
        String[] previous = null;
        for (int i = 0; i < keys.length; i++) {
            String old = MDC.get(keys[i]);
            if (!Objects.equals(old, values[i])) {
                if (previous == null) {
                    previous = new String[keys.length];
                    // the keys before were unchanged: their previous values are the new ones
                    System.arraycopy(values, 0, previous, 0, i);
                }
                set(keys[i], values[i]);
            }
            if (previous != null) {
                previous[i] = old;
            }
        }
        if (previous == null) {
            // the MDC already matches the context, e.g. the same context propagated again
            return scope;
        }
        return new MdcScope(scope, keys, previous);
    }

    private static void set(String key, @Nullable String value) {
        if (value == null) {
            MDC.remove(key);
        } else {
            MDC.put(key, value);
        }
    }

    /**
     * Restores the MDC values that were replaced when the context was made current.
     */
    private static final class MdcScope implements Scope {

        private final Scope scope;
        private final String[] keys;
        private final String[] previous;

        /**
         * @param scope    The OpenTelemetry scope
         * @param keys     The MDC keys
         * @param previous The previous MDC values, by key index
         */
        MdcScope(Scope scope, String[] keys, String[] previous) {
            this.scope = scope;
            this.keys = keys;
            this.previous = previous;
        }

        @Override
        public void close() {
            try {
                scope.close();
            } finally {
                // in reverse order: a key can be configured more than once (e.g. a baggage key named like the trace
                // id key), and its first write must be undone last
                for (int i = keys.length - 1; i >= 0; i--) {
                    set(keys[i], previous[i]);
                }
            }
        }
    }
}
