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
import jakarta.jms.Destination;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.Queue;
import jakarta.jms.TemporaryQueue;
import jakarta.jms.TemporaryTopic;
import jakarta.jms.Topic;
import org.jspecify.annotations.Nullable;

/**
 * A JMS message sent to or received from a destination, as seen by the instrumentation.
 *
 * @param message              The message
 * @param destinationName      The name of the destination, if known
 * @param temporaryDestination Whether the destination is a temporary queue or topic
 * @since 8.4.0
 */
@Internal
record JmsRequest(Message message, @Nullable String destinationName, boolean temporaryDestination) {

    /**
     * Creates a request for a message and the destination it is sent to.
     *
     * @param message     The message
     * @param destination The destination, if known
     * @return the request
     */
    static JmsRequest of(Message message, @Nullable Destination destination) {
        return new JmsRequest(
            message,
            destinationName(destination),
            destination instanceof TemporaryQueue || destination instanceof TemporaryTopic
        );
    }

    /**
     * Creates a request for a message received from a destination.
     *
     * @param message         The message
     * @param destinationName The name of the destination
     * @return the request
     */
    static JmsRequest of(Message message, @Nullable String destinationName) {
        return new JmsRequest(message, destinationName, false);
    }

    @Nullable
    private static String destinationName(@Nullable Destination destination) {
        try {
            if (destination instanceof Queue queue) {
                return queue.getQueueName();
            }
            if (destination instanceof Topic topic) {
                return topic.getTopicName();
            }
        } catch (JMSException | RuntimeException e) {
            // the destination name is informational only
        }
        return null;
    }
}
