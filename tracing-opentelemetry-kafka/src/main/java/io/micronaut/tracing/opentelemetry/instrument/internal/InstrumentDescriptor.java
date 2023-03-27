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

/**
 * A description of an OpenTelemetry metric instrument.
 * <p>
 * Based on original opentelemetry-kafka.
 *
 * @since 4.6.0
 */
class InstrumentDescriptor {

    static final String INSTRUMENT_TYPE_DOUBLE_OBSERVABLE_GAUGE = "DOUBLE_OBSERVABLE_GAUGE";
    static final String INSTRUMENT_TYPE_DOUBLE_OBSERVABLE_COUNTER = "DOUBLE_OBSERVABLE_COUNTER";

    private final String name;
    private final String description;
    private final String instrumentType;

    InstrumentDescriptor(String name, String description, String instrumentType) {
        this.name = name;
        this.description = description;
        this.instrumentType = instrumentType;
    }

    static InstrumentDescriptor createDoubleGauge(String name, String description) {
        return new InstrumentDescriptor(name, description, INSTRUMENT_TYPE_DOUBLE_OBSERVABLE_GAUGE);
    }

    static InstrumentDescriptor createDoubleCounter(String name, String description) {
        return new InstrumentDescriptor(name, description, INSTRUMENT_TYPE_DOUBLE_OBSERVABLE_COUNTER);
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getInstrumentType() {
        return instrumentType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        InstrumentDescriptor that = (InstrumentDescriptor) o;
        return Objects.equals(name, that.name) && Objects.equals(description, that.description) && Objects.equals(instrumentType, that.instrumentType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, description, instrumentType);
    }

    @Override
    public String toString() {
        return "InstrumentDescriptor{" +
            "name='" + name + '\'' +
            ", description='" + description + '\'' +
            ", instrumentType='" + instrumentType + '\'' +
            '}';
    }
}
