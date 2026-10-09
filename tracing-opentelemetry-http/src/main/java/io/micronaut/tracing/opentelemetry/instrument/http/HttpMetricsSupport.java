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
package io.micronaut.tracing.opentelemetry.instrument.http;

import io.micronaut.context.BeanContext;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.tracing.opentelemetry.instrument.util.DefaultOperationMetrics;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.OperationMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Creates the default HTTP {@link OperationMetrics} of the Micronaut HTTP instrumenters.
 *
 * @author Graeme Rocher
 * @since 8.4.0
 */
@Internal
public final class HttpMetricsSupport {

    private static final Logger LOG = LoggerFactory.getLogger(HttpMetricsSupport.class);

    private HttpMetricsSupport() {
    }

    /**
     * Creates the default HTTP metrics, enabled according to the configuration and the meter provider. When
     * they are enabled and the micronaut-micrometer web metrics filter is active too, logs that every request
     * is timed twice.
     *
     * @param metrics                   the metrics
     * @param configured                the configured {@code enabled} value, {@code null} if not configured
     * @param openTelemetry             the OpenTelemetry instance
     * @param instrumentationName       the instrumentation scope name
     * @param beanContext               the bean context
     * @param side                      {@code server} or {@code client}
     * @param micrometerFilterClassName the micronaut-micrometer web metrics filter class for this side
     * @param metricName                the OpenTelemetry metric name
     * @return the default metrics
     */
    @NonNull
    public static DefaultOperationMetrics defaultMetrics(@NonNull OperationMetrics metrics,
                                                         @Nullable Boolean configured,
                                                         @NonNull OpenTelemetry openTelemetry,
                                                         @NonNull String instrumentationName,
                                                         @NonNull BeanContext beanContext,
                                                         @NonNull String side,
                                                         @NonNull String micrometerFilterClassName,
                                                         @NonNull String metricName) {
        boolean enabled = DefaultOperationMetrics.isEnabled(configured, openTelemetry, instrumentationName);
        if (enabled && micrometerWebMetricsActive(beanContext, micrometerFilterClassName)) {
            LOG.warn("HTTP {} requests are timed twice: by OpenTelemetry ({}) and by the Micronaut Micrometer web metrics binder (http.{}.requests). "
                    + "Set tracing.opentelemetry.http.{}.metrics.enabled=false or micronaut.metrics.binders.web.enabled=false to record only one of them.",
                side, metricName, side, side);
        }
        return new DefaultOperationMetrics(metrics, enabled);
    }

    private static boolean micrometerWebMetricsActive(BeanContext beanContext, String filterClassName) {
        return ClassUtils.forName(filterClassName, beanContext.getClassLoader())
            .map(type -> {
                try {
                    return beanContext.containsBean(type);
                } catch (RuntimeException | LinkageError e) {
                    return false;
                }
            })
            .orElse(false);
    }
}
