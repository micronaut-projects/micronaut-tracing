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
package io.micronaut.tracing.opentelemetry.xray;

import io.micronaut.context.BeanProvider;
import io.micronaut.core.annotation.Internal;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.awssdk.v2_2.AwsSdkTelemetry;
import software.amazon.awssdk.core.interceptor.ExecutionInterceptor;

import java.util.function.Function;

/**
 * Provides AWS SDK telemetry operations without exposing the OpenTelemetry AWS SDK implementation as a bean.
 *
 * <p>{@link OpenTelemetry} is resolved lazily, the first time telemetry is actually needed, so that creating
 * this provider (and the listeners that depend on it) does not eagerly instantiate {@link OpenTelemetry}. This
 * avoids a circular dependency when an OpenTelemetry exporter or resource bean itself requires an AWS SDK
 * client. The resulting {@link AwsSdkTelemetry} is created once and shared.</p>
 *
 * @author Nemanja Mikic
 * @since 8.0.0
 */
@Internal
final class AwsSdkTelemetryProvider {
    private final BeanProvider<OpenTelemetry> openTelemetryProvider;
    private final AwsSdkTelemetryConfiguration awsSdkTelemetryConfiguration;
    private final MessagingTelemetryConfiguration messagingTelemetryConfiguration;
    private volatile AwsSdkTelemetry awsSdkTelemetry;

    AwsSdkTelemetryProvider(BeanProvider<OpenTelemetry> openTelemetryProvider,
                            AwsSdkTelemetryConfiguration awsSdkTelemetryConfiguration,
                            MessagingTelemetryConfiguration messagingTelemetryConfiguration) {
        this.openTelemetryProvider = openTelemetryProvider;
        this.awsSdkTelemetryConfiguration = awsSdkTelemetryConfiguration;
        this.messagingTelemetryConfiguration = messagingTelemetryConfiguration;
    }

    ExecutionInterceptor newExecutionInterceptor() {
        return withTelemetry(AwsSdkTelemetry::createExecutionInterceptor);
    }

    <T> T withTelemetry(Function<AwsSdkTelemetry, T> function) {
        // no method signature exposes AwsSdkTelemetry directly, keeping reflective introspection of this class
        // free of optional AWS SDK service types referenced by AwsSdkTelemetry
        AwsSdkTelemetry telemetry = awsSdkTelemetry;
        if (telemetry == null) {
            synchronized (this) {
                telemetry = awsSdkTelemetry;
                if (telemetry == null) {
                    telemetry = AwsSdkTelemetry.builder(openTelemetryProvider.get())
                        .setCaptureExperimentalSpanAttributes(awsSdkTelemetryConfiguration.isExperimentalSpanAttributes())
                        .setUseConfiguredPropagatorForMessaging(awsSdkTelemetryConfiguration.isExperimentalUsePropagatorForMessaging())
                        .setMessagingReceiveTelemetryEnabled(messagingTelemetryConfiguration.isEnabled())
                        .build();
                    awsSdkTelemetry = telemetry;
                }
            }
        }
        return function.apply(telemetry);
    }
}
