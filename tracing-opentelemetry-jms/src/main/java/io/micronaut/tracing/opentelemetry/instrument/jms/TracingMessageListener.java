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
package io.micronaut.tracing.opentelemetry.instrument.jms;

import io.micronaut.core.annotation.Internal;
import jakarta.jms.Message;
import jakarta.jms.MessageListener;
import org.jspecify.annotations.Nullable;

/**
 * A {@link MessageListener} that processes each message in a {@code CONSUMER} span.
 *
 * @since 8.4.0
 */
@Internal
final class TracingMessageListener implements MessageListener {

    private final MessageListener delegate;
    @Nullable
    private final String destinationName;
    private final JmsTelemetry telemetry;

    TracingMessageListener(MessageListener delegate, @Nullable String destinationName, JmsTelemetry telemetry) {
        this.delegate = delegate;
        this.destinationName = destinationName;
        this.telemetry = telemetry;
    }

    @Override
    public void onMessage(Message message) {
        telemetry.process(delegate, message, destinationName);
    }

    @Override
    public String toString() {
        return "TracingMessageListener(" + delegate + ")";
    }
}
