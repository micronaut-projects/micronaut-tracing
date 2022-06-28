/*
 * Copyright 2017-2022 original authors
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
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.awssdk.v2_2.AwsSdkTelemetry;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.client.builder.SdkClientBuilder;

/**
 * Configures a Tracing Interceptor for all sdk client builders.
 * @see <a href="https://aws-otel.github.io/docs/getting-started/java-sdk/trace-manual-instr">Instrumenting the AWS SDK</a>
 *
 * @author Sergio del Amo
 * @since 4.2.0
 */
@Requires(classes = AwsSdkTelemetry.class)
@Requires(classes = SdkClientBuilder.class)
@Singleton
public class SdkClientBuilderListener implements BeanCreatedEventListener<SdkClientBuilder<?, ?>> {
    private static final Logger LOG = LoggerFactory.getLogger(SdkClientBuilderListener.class);

    private final OpenTelemetry openTelemetry;

    /**
     *
     * @param openTelemetry OpenTelemetry
     */
    public SdkClientBuilderListener(OpenTelemetry openTelemetry) {
        this.openTelemetry = openTelemetry;
    }

    /**
     * Add an OpenTelemetry execution interceptor to {@link SdkClientBuilder}.
     *
     * @param event bean created event
     * @return sdk client builder
     */
    @Override
    public SdkClientBuilder<?, ?> onCreated(BeanCreatedEvent<SdkClientBuilder<?, ?>> event) {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Registering OpenTelemetry tracing interceptor to {}", event.getBean().getClass().getSimpleName());
        }
        return event.getBean().overrideConfiguration(builder ->
            builder.addExecutionInterceptor(AwsSdkTelemetry.create(openTelemetry).newExecutionInterceptor()));
    }
}
