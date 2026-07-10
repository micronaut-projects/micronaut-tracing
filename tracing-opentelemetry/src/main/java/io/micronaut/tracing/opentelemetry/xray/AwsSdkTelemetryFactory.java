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

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.awssdk.v2_2.AwsSdkTelemetry;
import jakarta.inject.Singleton;
import software.amazon.awssdk.core.client.builder.SdkClientBuilder;

/**
 * Creates the OpenTelemetry AWS SDK instrumentation.
 *
 * @author Nemanja Mikic
 * @since 8.0.0
 */
@Internal
@Factory
@Requires(classes = {AwsSdkTelemetry.class, SdkClientBuilder.class})
public class AwsSdkTelemetryFactory {

    /**
     * @param openTelemetry OpenTelemetry
     * @param awsSdkTelemetryConfiguration AWS SDK instrumentation configuration
     * @param messagingTelemetryConfiguration messaging instrumentation configuration
     * @return the AWS SDK telemetry instrumentation
     */
    @Singleton
    AwsSdkTelemetryProvider awsSdkTelemetryProvider(OpenTelemetry openTelemetry,
                                                    AwsSdkTelemetryConfiguration awsSdkTelemetryConfiguration,
                                                    MessagingTelemetryConfiguration messagingTelemetryConfiguration) {
        return new AwsSdkTelemetryProvider(AwsSdkTelemetry.builder(openTelemetry)
            .setCaptureExperimentalSpanAttributes(awsSdkTelemetryConfiguration.isExperimentalSpanAttributes())
            .setUseConfiguredPropagatorForMessaging(awsSdkTelemetryConfiguration.isExperimentalUsePropagatorForMessaging())
            .setMessagingReceiveTelemetryEnabled(messagingTelemetryConfiguration.isEnabled())
            .build());
    }
}
