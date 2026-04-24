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
package io.micronaut.tracing.opentelemetry.instrument.rabbitmq;

import io.opentelemetry.context.propagation.TextMapGetter;

import java.util.Collections;
import java.util.Map;

enum RabbitMQHeadersGetter implements TextMapGetter<Map<String, Object>> {
    INSTANCE;

    @Override
    public Iterable<String> keys(Map<String, Object> carrier) {
        return carrier == null ? Collections.emptyList() : carrier.keySet();
    }

    @Override
    public String get(Map<String, Object> carrier, String key) {
        if (carrier == null) {
            return null;
        }
        Object value = carrier.get(key);
        return value == null ? null : value.toString();
    }
}
