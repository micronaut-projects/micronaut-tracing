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

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.code.CodeAttributesGetter;
import io.opentelemetry.instrumentation.api.instrumenter.code.CodeSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientMetrics;
import io.opentelemetry.instrumentation.api.util.ClassAndMethod;


/**
 * An HTTP client instrumentation builder for Open Telemetry.
 *
 * @author Nemanja Mikic
 */
public final class MicronautCodeTelemetryBuilder {

    private static final String INSTRUMENTATION_NAME = "io.micronaut.code";

    private final OpenTelemetry openTelemetry;

    public MicronautCodeTelemetryBuilder(OpenTelemetry openTelemetry) {
        this.openTelemetry = openTelemetry;
    }

    public Instrumenter<ClassAndMethod, Object> build() {
        CodeAttributesGetter<ClassAndMethod> classAndMethodAttributesGetter = ClassAndMethod.codeAttributesGetter();
        InstrumenterBuilder<ClassAndMethod, Object> builder = Instrumenter.builder(openTelemetry, INSTRUMENTATION_NAME, CodeSpanNameExtractor.create(classAndMethodAttributesGetter));

        return builder.addOperationMetrics(HttpClientMetrics.get())
            .newInstrumenter();
    }

}
