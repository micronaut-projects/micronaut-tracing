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

import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.Nullable;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.tracing.opentelemetry.conf.OpenTelemetryConfigurationProperties;
import jakarta.annotation.PreDestroy;
import org.slf4j.MDC;
import org.slf4j.helpers.NOPMDCAdapter;

/**
 * Installs the automatic log correlation: while Micronaut propagates an OpenTelemetry context, the trace id,
 * span id and trace flags of its span (and the configured baggage entries) are in the SLF4J {@link MDC}.
 *
 * @since 8.4.0
 */
@Internal
@Context
@Requires(classes = MDC.class)
@Requires(property = OpenTelemetryConfigurationProperties.PREFIX + ".enabled", notEquals = StringUtils.FALSE)
@Requires(property = OpenTelemetryMdcConfiguration.PREFIX + ".enabled", notEquals = StringUtils.FALSE)
final class OpenTelemetryMdcInstaller {

    /**
     * The OpenTelemetry logback-mdc appender, which adds the trace context to the log events itself.
     */
    static final String LOGBACK_MDC_APPENDER = "io.opentelemetry.instrumentation.logback.mdc.v1_0.OpenTelemetryAppender";

    @Nullable
    private final MdcTraceCorrelation correlation;

    OpenTelemetryMdcInstaller(OpenTelemetryMdcConfiguration configuration) {
        if (isEnabled(configuration)) {
            correlation = new MdcTraceCorrelation(
                configuration.getTraceIdKey(),
                configuration.getSpanIdKey(),
                configuration.getTraceFlagsKey(),
                configuration.getBaggageKeys()
            );
            MdcTraceCorrelation.install(correlation);
        } else {
            correlation = null;
        }
    }

    private static boolean isEnabled(OpenTelemetryMdcConfiguration configuration) {
        if (MDC.getMDCAdapter() instanceof NOPMDCAdapter) {
            // no SLF4J provider supporting the MDC
            return false;
        }
        Boolean enabled = configuration.getEnabled();
        if (enabled != null) {
            return enabled;
        }
        // back off by default when the user wraps the appenders with the OpenTelemetry logback-mdc appender
        return !ClassUtils.isPresent(LOGBACK_MDC_APPENDER, OpenTelemetryMdcInstaller.class.getClassLoader());
    }

    /**
     * Uninstalls the log correlation.
     */
    @PreDestroy
    void close() {
        if (correlation != null) {
            MdcTraceCorrelation.uninstall(correlation);
        }
    }
}
