/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.tracing.opentelemetry.instrument.internal;

import java.util.Objects;

import io.opentelemetry.api.common.Attributes;

import org.apache.kafka.common.MetricName;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 * <p>
 * Based on original opentelemetry-kafka.
 *
 * @since 4.6.0
 */
class RegisteredObservable {

    private final MetricName kafkaMetricName;
    private final InstrumentDescriptor instrumentDescriptor;
    private final Attributes attributes;
    private final AutoCloseable observable;

    RegisteredObservable(MetricName kafkaMetricName, InstrumentDescriptor instrumentDescriptor, Attributes attributes, AutoCloseable observable) {
        this.kafkaMetricName = kafkaMetricName;
        this.instrumentDescriptor = instrumentDescriptor;
        this.attributes = attributes;
        this.observable = observable;
    }

    public MetricName getKafkaMetricName() {
        return kafkaMetricName;
    }

    public InstrumentDescriptor getInstrumentDescriptor() {
        return instrumentDescriptor;
    }

    public Attributes getAttributes() {
        return attributes;
    }

    public AutoCloseable getObservable() {
        return observable;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        RegisteredObservable that = (RegisteredObservable) o;
        return Objects.equals(kafkaMetricName, that.kafkaMetricName) && Objects.equals(instrumentDescriptor, that.instrumentDescriptor) && Objects.equals(attributes, that.attributes) && Objects.equals(observable, that.observable);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kafkaMetricName, instrumentDescriptor, attributes, observable);
    }

    @Override
    public String toString() {
        return "RegisteredObservable{" +
            "kafkaMetricName=" + kafkaMetricName +
            ", instrumentDescriptor=" + instrumentDescriptor +
            ", attributes=" + attributes +
            ", observable=" + observable +
            '}';
    }
}
