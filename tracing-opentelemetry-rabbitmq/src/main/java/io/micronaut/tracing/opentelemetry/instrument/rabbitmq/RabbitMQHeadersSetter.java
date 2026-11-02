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

import io.opentelemetry.context.propagation.TextMapSetter;

import java.util.Map;

final class RabbitMQHeadersSetter implements TextMapSetter<Map<String, Object>> {

    @Override
    public void set(Map<String, Object> carrier, String key, String value) {
        if (carrier != null) {
            carrier.put(key, value);
        }
    }
}
