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

import com.rabbitmq.client.LongString;
import io.opentelemetry.context.propagation.TextMapGetter;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;

final class RabbitMQHeadersGetter implements TextMapGetter<Map<String, Object>> {

    static final int MAX_PROPAGATION_HEADER_VALUE_BYTES = 8192;

    @Override
    public Iterable<String> keys(Map<String, Object> carrier) {
        return carrier == null ? Set.of() : carrier.keySet();
    }

    @Override
    public String get(Map<String, Object> carrier, String key) {
        if (carrier == null) {
            return null;
        }
        Object value = carrier.get(key);
        if (value instanceof String string) {
            return string.length() <= MAX_PROPAGATION_HEADER_VALUE_BYTES ? string : null;
        }
        if (value instanceof LongString longString) {
            if (longString.length() > MAX_PROPAGATION_HEADER_VALUE_BYTES) {
                return null;
            }
            return new String(longString.getBytes(), StandardCharsets.UTF_8);
        }
        if (value instanceof byte[] bytes) {
            if (bytes.length > MAX_PROPAGATION_HEADER_VALUE_BYTES) {
                return null;
            }
            return new String(bytes, StandardCharsets.UTF_8);
        }
        return null;
    }
}
