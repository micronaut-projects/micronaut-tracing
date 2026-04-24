/*
 * Copyright 2017-2025 original authors
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

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.context.event.StartupEvent;
import io.micronaut.core.util.StringUtils;
import io.micronaut.tracing.opentelemetry.conf.OpenTelemetryConfigurationProperties;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import jakarta.inject.Singleton;

/**
 * Installs the OpenTelemetry Logback appender when it is present on the classpath.
 */
@Singleton
@Requires(classes = OpenTelemetryAppender.class)
@Requires(property = OpenTelemetryConfigurationProperties.PREFIX + ".enabled", notEquals = StringUtils.FALSE)
@Requires(beans = OpenTelemetry.class)
public final class OpenTelemetryLogbackAppenderInstaller implements ApplicationEventListener<StartupEvent> {

    private final OpenTelemetry openTelemetry;

    OpenTelemetryLogbackAppenderInstaller(OpenTelemetry openTelemetry) {
        this.openTelemetry = openTelemetry;
    }

    @Override
    public void onApplicationEvent(StartupEvent event) {
        OpenTelemetryAppender.install(openTelemetry);
    }
}
