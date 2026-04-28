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

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import io.micronaut.core.annotation.Internal;
import io.opentelemetry.instrumentation.awssdk.v2_2.AwsSdkTelemetry;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

/**
 * Wraps asynchronous SQS clients for OpenTelemetry message propagation.
 *
 * @author Nemanja Mikic
 * @since 8.0.0
 */
@Internal
@Requires(classes = {AwsSdkTelemetry.class, SqsAsyncClient.class})
@Singleton
public class SqsAsyncClientBeanCreatedEventListener implements BeanCreatedEventListener<SqsAsyncClient> {
    private static final Logger LOG = LoggerFactory.getLogger(SqsAsyncClientBeanCreatedEventListener.class);

    private final AwsSdkTelemetryProvider awsSdkTelemetryProvider;
    private final AwsSdkTelemetryConfiguration awsSdkTelemetryConfiguration;
    private final MessagingTelemetryConfiguration messagingTelemetryConfiguration;

    /**
     * @param awsSdkTelemetryProvider AWS SDK telemetry provider
     * @param awsSdkTelemetryConfiguration AWS SDK instrumentation configuration
     * @param messagingTelemetryConfiguration messaging instrumentation configuration
     */
    public SqsAsyncClientBeanCreatedEventListener(AwsSdkTelemetryProvider awsSdkTelemetryProvider,
                                                  AwsSdkTelemetryConfiguration awsSdkTelemetryConfiguration,
                                                  MessagingTelemetryConfiguration messagingTelemetryConfiguration) {
        this.awsSdkTelemetryProvider = awsSdkTelemetryProvider;
        this.awsSdkTelemetryConfiguration = awsSdkTelemetryConfiguration;
        this.messagingTelemetryConfiguration = messagingTelemetryConfiguration;
    }

    @Override
    public SqsAsyncClient onCreated(BeanCreatedEvent<SqsAsyncClient> event) {
        if (!shouldWrap()) {
            return event.getBean();
        }
        if (LOG.isTraceEnabled()) {
            LOG.trace("Wrapping OpenTelemetry asynchronous SQS client {}", event.getBean().getClass().getSimpleName());
        }
        return awsSdkTelemetryProvider.wrap(event.getBean());
    }

    private boolean shouldWrap() {
        return awsSdkTelemetryConfiguration.isExperimentalUsePropagatorForMessaging() || messagingTelemetryConfiguration.isEnabled();
    }
}
