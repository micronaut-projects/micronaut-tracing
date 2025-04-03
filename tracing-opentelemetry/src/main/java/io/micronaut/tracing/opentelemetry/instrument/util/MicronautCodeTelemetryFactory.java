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
import io.micronaut.core.annotation.Nullable;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.semconv.code.CodeSpanNameExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.util.ClassAndMethod;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.ContextCustomizer;
import io.opentelemetry.instrumentation.api.instrumenter.ErrorCauseExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.OperationListener;
import io.opentelemetry.instrumentation.api.instrumenter.OperationMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.SpanLinksExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanStatusExtractor;
import io.opentelemetry.sdk.common.internal.OtelVersion;
import jakarta.inject.Named;
import jakarta.inject.Qualifier;
import jakarta.inject.Singleton;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.util.List;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

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
     * Internal Qualifier represents AttributesExtractor that should be used in code instrumenter.
     */
    @Qualifier
    @Documented
    @Retention(RUNTIME)
    public @interface Internal { }

    /**
     * Builds the code Open Telemetry instrumenter.
     * @param openTelemetry the {@link OpenTelemetry}
     * @param spanNameExtractor the {@link SpanNameExtractor}
     * @param spanStatusExtractor the {@link SpanStatusExtractor}, can be {@code null}
     * @param errorCauseExtractor the {@link ErrorCauseExtractor}, can be {@code null}
     * @param attributesExtractors the list of {@link AttributesExtractor}
     * @param contextCustomizers the list of {@link ContextCustomizer}
     * @param operationMetrics the list of {@link OperationMetrics}
     * @param operationListeners the list of {@link OperationListener}
     * @param spanLinksExtractors the list of {@link SpanLinksExtractor}
     * @return the OpenTelemetry bean with default values
     */
    @Prototype
    @Requires(beans = OpenTelemetry.class)
    @SuppressWarnings("DuplicatedCode")
    @Named("micronautCodeTelemetryInstrumenter")
    public Instrumenter<ClassAndMethod, Object> instrumenter(
        OpenTelemetry openTelemetry,
        @Internal SpanNameExtractor<ClassAndMethod> spanNameExtractor,
        @Internal @Nullable SpanStatusExtractor<ClassAndMethod, Object> spanStatusExtractor,
        @Internal @Nullable ErrorCauseExtractor errorCauseExtractor,
        @Internal List<AttributesExtractor<ClassAndMethod, Object>> attributesExtractors,
        @Internal List<ContextCustomizer<ClassAndMethod>> contextCustomizers,
        @Internal List<OperationMetrics> operationMetrics,
        @Internal List<OperationListener> operationListeners,
        @Internal List<SpanLinksExtractor<ClassAndMethod>> spanLinksExtractors) {

        InstrumenterBuilder<ClassAndMethod, Object> builder = Instrumenter.builder(
            openTelemetry, INSTRUMENTATION_NAME, spanNameExtractor);

        builder.setInstrumentationVersion(OtelVersion.VERSION);
        if (spanStatusExtractor != null) {
            builder.setSpanStatusExtractor(spanStatusExtractor);
        }
        if (errorCauseExtractor != null) {
            builder.setErrorCauseExtractor(errorCauseExtractor);
        }
        builder.addAttributesExtractors(attributesExtractors);
        contextCustomizers.forEach(builder::addContextCustomizer);
        operationListeners.forEach(builder::addOperationListener);
        operationMetrics.forEach(builder::addOperationMetrics);
        spanLinksExtractors.forEach(builder::addSpanLinksExtractor);

        return builder.buildInstrumenter();
    }

    /**
     * Builds the default {@link SpanNameExtractor}.
     * @return the {@link CodeSpanNameExtractor}
     */
    @Internal
    @Singleton
    SpanNameExtractor<ClassAndMethod> defaultSpanNameExtractor() {
        return CodeSpanNameExtractor.create(ClassAndMethod.codeAttributesGetter());
    }
}
