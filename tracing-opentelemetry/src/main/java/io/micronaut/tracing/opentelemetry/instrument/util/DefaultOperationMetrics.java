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
package io.micronaut.tracing.opentelemetry.instrument.util;

import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.MeterProvider;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.OperationListener;
import io.opentelemetry.instrumentation.api.instrumenter.OperationMetrics;

import java.util.List;
import java.util.Objects;

/**
 * An {@link OperationMetrics} registered by default by the Micronaut instrumenter factories that is only
 * applied to the instrumenter when it is {@link #isEnabled() enabled}.
 *
 * <p>Metric listeners run for every operation (they copy the {@code Context} and merge the start and end
 * attributes) even when the metrics are dropped, so the factories only apply the default metrics when they
 * are exported. {@link OperationMetrics} beans contributed by the application are not wrapped and are always
 * applied.</p>
 *
 * @author Graeme Rocher
 * @since 8.4.0
 */
@Internal
public final class DefaultOperationMetrics implements OperationMetrics {

    private final OperationMetrics delegate;
    private final boolean enabled;

    /**
     * @param delegate the metrics to apply when enabled
     * @param enabled  whether the metrics are applied
     */
    public DefaultOperationMetrics(@NonNull OperationMetrics delegate, boolean enabled) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.enabled = enabled;
    }

    /**
     * @return whether the metrics are applied to the instrumenter
     */
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public OperationListener create(Meter meter) {
        return delegate.create(meter);
    }

    /**
     * Adds the given metrics to the builder, skipping disabled {@link DefaultOperationMetrics}.
     *
     * @param builder          the instrumenter builder
     * @param operationMetrics the metrics
     */
    public static void addTo(@NonNull InstrumenterBuilder<?, ?> builder, @NonNull List<OperationMetrics> operationMetrics) {
        for (OperationMetrics metrics : operationMetrics) {
            if (!(metrics instanceof DefaultOperationMetrics defaultMetrics) || defaultMetrics.enabled) {
                builder.addOperationMetrics(metrics);
            }
        }
    }

    /**
     * Resolves whether default metrics should be recorded.
     *
     * <p>An explicitly configured value wins. Otherwise the metrics are enabled only when the
     * {@link MeterProvider} of the given {@link OpenTelemetry} records metrics. This covers every way of
     * configuring metrics: an {@code otel.metrics.exporter} other than {@code none} (Micronaut's default),
     * a {@code MetricReader} registered through an {@code OpenTelemetryBuilderCustomizer}, or an
     * {@link OpenTelemetry} instance provided by the application or the Java agent. The OpenTelemetry SDK
     * hands out no-op meters when its meter provider has no metric reader.</p>
     *
     * @param configured          the configured value, {@code null} if not configured
     * @param openTelemetry       the OpenTelemetry instance
     * @param instrumentationName the instrumentation scope name used to probe the meter provider
     * @return whether the default metrics should be recorded
     */
    public static boolean isEnabled(@Nullable Boolean configured, @NonNull OpenTelemetry openTelemetry, @NonNull String instrumentationName) {
        if (configured != null) {
            return configured;
        }
        return recordsMetrics(openTelemetry, instrumentationName);
    }

    /**
     * Checks whether the meter provider of the given {@link OpenTelemetry} records metrics.
     *
     * @param openTelemetry       the OpenTelemetry instance
     * @param instrumentationName the instrumentation scope name used to probe the meter provider
     * @return {@code false} if the meter provider only hands out no-op meters
     */
    public static boolean recordsMetrics(@NonNull OpenTelemetry openTelemetry, @NonNull String instrumentationName) {
        MeterProvider meterProvider = openTelemetry.getMeterProvider();
        MeterProvider noop = MeterProvider.noop();
        if (meterProvider == null || meterProvider == noop) {
            return false;
        }
        return meterProvider.get(instrumentationName).getClass() != noop.get(instrumentationName).getClass();
    }
}
