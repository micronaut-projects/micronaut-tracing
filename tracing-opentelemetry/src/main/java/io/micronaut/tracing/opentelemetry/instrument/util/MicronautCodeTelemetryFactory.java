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
package io.micronaut.tracing.opentelemetry.instrument.util;

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.annotation.Requires;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.semconv.code.CodeAttributesGetter;
import io.opentelemetry.instrumentation.api.incubator.semconv.code.CodeSpanNameExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.util.ClassAndMethod;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.semconv.http.HttpClientMetrics;
import jakarta.inject.Named;

/**
 * An HTTP client instrumentation factory for Open Telemetry.
 *
 * @author Nemanja Mikic
 * @since 4.2.0
 */
@Factory
public final class MicronautCodeTelemetryFactory {

    private static final String INSTRUMENTATION_NAME = "io.micronaut.code";

    /**
     * Builds the code Open Telemetry instrumenter.
     * @param openTelemetry the {@link OpenTelemetry}
     * @return the OpenTelemetry bean with default values
     */
    @Prototype
    @Requires(beans = OpenTelemetry.class)
    @Named("micronautCodeTelemetryInstrumenter")
    public Instrumenter<ClassAndMethod, Object> instrumenter(OpenTelemetry openTelemetry) {
        CodeAttributesGetter<ClassAndMethod> classAndMethodAttributesGetter = ClassAndMethod.codeAttributesGetter();
        InstrumenterBuilder<ClassAndMethod, Object> builder = Instrumenter.builder(
            openTelemetry, INSTRUMENTATION_NAME, CodeSpanNameExtractor.create(classAndMethodAttributesGetter));

        return builder.addOperationMetrics(HttpClientMetrics.get())
            .buildInstrumenter();
    }
}
