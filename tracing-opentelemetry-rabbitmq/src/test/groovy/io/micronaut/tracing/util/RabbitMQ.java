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
package io.micronaut.tracing.util;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy;

import java.util.Map;

public final class RabbitMQ {
    private static GenericContainer<?> container;

    private RabbitMQ() {
    }

    public static Map<String, String> getProperties() {
        if (container == null) {
            container = new GenericContainer<>("rabbitmq:3.13")
                .withExposedPorts(5672)
                .waitingFor(new LogMessageWaitStrategy().withRegEx("(?s).*Server startup complete.*"));
            container.start();
        }
        return Map.of(
            "rabbitmq.uri", "amqp://guest:guest@%s:%d".formatted(container.getHost(), container.getMappedPort(5672)),
            "rabbitmq.username", "guest",
            "rabbitmq.password", "guest"
        );
    }
}
