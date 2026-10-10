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
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingAttributesGetter;
import jakarta.jms.JMSException;
import org.jspecify.annotations.Nullable;

/**
 * Exposes the messaging attributes of a JMS message.
 *
 * @since 8.4.0
 */
@Internal
enum JmsMessagingAttributesGetter implements MessagingAttributesGetter<JmsRequest, Void> {

    INSTANCE;

    /**
     * The value of the {@code messaging.system} attribute.
     */
    static final String SYSTEM = "jms";

    @Override
    public String getSystem(JmsRequest request) {
        return SYSTEM;
    }

    @Override
    @Nullable
    public String getDestination(JmsRequest request) {
        return request.destinationName();
    }

    @Override
    @Nullable
    public String getDestinationTemplate(JmsRequest request) {
        return null;
    }

    @Override
    public boolean isTemporaryDestination(JmsRequest request) {
        return request.temporaryDestination();
    }

    @Override
    public boolean isAnonymousDestination(JmsRequest request) {
        return false;
    }

    @Override
    @Nullable
    public String getConversationId(JmsRequest request) {
        try {
            return request.message().getJMSCorrelationID();
        } catch (JMSException | RuntimeException e) {
            return null;
        }
    }

    @Override
    @Nullable
    public Long getMessageBodySize(JmsRequest request) {
        return null;
    }

    @Override
    @Nullable
    public Long getMessageEnvelopeSize(JmsRequest request) {
        return null;
    }

    @Override
    @Nullable
    public String getMessageId(JmsRequest request, @Nullable Void unused) {
        try {
            return request.message().getJMSMessageID();
        } catch (JMSException | RuntimeException e) {
            return null;
        }
    }

    @Override
    @Nullable
    public String getClientId(JmsRequest request) {
        return null;
    }

    @Override
    @Nullable
    public Long getBatchMessageCount(JmsRequest request, @Nullable Void unused) {
        return null;
    }
}
