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
package io.micronaut.tracing.opentracing;

import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import io.micronaut.core.annotation.Internal;
import io.opentracing.Tracer;
import io.opentracing.noop.NoopTracer;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Logs a warning, once per JVM, when an OpenTracing {@link Tracer} bean (for example the Jaeger or the Brave
 * OpenTracing bridge tracer) is created, since the OpenTracing integration is deprecated for removal.
 * The default no-op tracer is ignored.
 *
 * @author graemerocher
 * @since 8.4.0
 * @deprecated OpenTracing is archived. Use Micronaut Tracing OpenTelemetry instead.
 */
@Deprecated(since = "8.4.0", forRemoval = true)
@Internal
@Singleton
final class OpenTracingDeprecationWarning implements BeanCreatedEventListener<Tracer> {

    static final String MESSAGE = "OpenTracing tracer [{}] created. The OpenTracing based integrations of Micronaut Tracing "
        + "(micronaut-tracing-opentracing, micronaut-tracing-jaeger and the OpenTracing bridge of micronaut-tracing-brave) "
        + "are deprecated and will be removed in the next major version. Migrate to micronaut-tracing-opentelemetry, see "
        + "https://micronaut-projects.github.io/micronaut-tracing/latest/guide/#migrationFromOpenTracing";

    private static final Logger LOG = LoggerFactory.getLogger(OpenTracingDeprecationWarning.class);

    private static final AtomicBoolean WARNED = new AtomicBoolean();

    @Override
    public Tracer onCreated(BeanCreatedEvent<Tracer> event) {
        Tracer tracer = event.getBean();
        if (!(tracer instanceof NoopTracer) && WARNED.compareAndSet(false, true)) {
            LOG.warn(MESSAGE, tracer.getClass().getName());
        }
        return tracer;
    }

    /**
     * Allows the warning to be logged again. Used in tests.
     */
    static void reset() {
        WARNED.set(false);
    }
}
