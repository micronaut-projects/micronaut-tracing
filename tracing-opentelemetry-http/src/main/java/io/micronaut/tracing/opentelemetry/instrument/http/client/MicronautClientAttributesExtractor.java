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
package io.micronaut.tracing.opentelemetry.instrument.http.client;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpRequest;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;

import java.util.List;

/**
 * Wraps List of {@link AttributesExtractor} that will be used inside http client instrumenter.
 *
 * @author Nemanja Mikic
 * @since 4.1.0
 */
public class MicronautClientAttributesExtractor {

    final List<AttributesExtractor<MutableHttpRequest<?>, HttpResponse<?>>> attributesExtractors;

    public MicronautClientAttributesExtractor(List<AttributesExtractor<MutableHttpRequest<?>, HttpResponse<?>>> attributesExtractors) {
        this.attributesExtractors = attributesExtractors;
    }

    /**
     * @return The List of {@link AttributesExtractor}
     */
    public List<AttributesExtractor<MutableHttpRequest<?>, HttpResponse<?>>> getAttributesExtractors() {
        return attributesExtractors;
    }
}
