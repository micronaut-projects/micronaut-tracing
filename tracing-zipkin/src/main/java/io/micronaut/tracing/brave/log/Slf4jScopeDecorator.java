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
package io.micronaut.tracing.brave.log;

import brave.internal.codec.HexCodec;
import brave.propagation.CurrentTraceContext.Scope;
import brave.propagation.CurrentTraceContext.ScopeDecorator;
import brave.propagation.TraceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * Adds {@linkplain MDC} properties "traceId", "parentId", "spanId" and "spanExportable"
 * when a {@link brave.Tracer#currentSpan() span is current}. These can be used in log
 * correlation. Supports backward compatibility of MDC entries by adding legacy "X-B3"
 * entries to MDC context "X-B3-TraceId", "X-B3-ParentSpanId", "X-B3-SpanId" and
 * "X-B3-Sampled"
 * <p>
 * Forked from <a href="https://github.com/spring-cloud/spring-cloud-sleuth/blob/master/spring-cloud-sleuth-core/src/main/java/org/springframework/cloud/sleuth/log/Slf4jScopeDecorator.java">link</a>
 *
 * @author Marcin Grzejszczak
 * @author Graeme Rocher
 * @since 1.0.0
 */
final class Slf4jScopeDecorator implements ScopeDecorator {

    private static final Logger LOG = LoggerFactory.getLogger(Slf4jScopeDecorator.class);

    // Backward compatibility for all logging patterns
    private static final String LEGACY_EXPORTABLE_NAME = "X-Span-Export";
    private static final String LEGACY_PARENT_ID_NAME = "X-B3-ParentSpanId";
    private static final String LEGACY_TRACE_ID_NAME = "X-B3-TraceId";
    private static final String LEGACY_SPAN_ID_NAME = "X-B3-SpanId";

    @Override
    public Scope decorateScope(TraceContext currentSpan, Scope scope) {
        final String previousTraceId = MDC.get("traceId");
        final String previousParentId = MDC.get("parentId");
        final String previousSpanId = MDC.get("spanId");
        final String spanExportable = MDC.get("spanExportable");
        final String legacyPreviousTraceId = MDC.get(LEGACY_TRACE_ID_NAME);
        final String legacyPreviousParentId = MDC.get(LEGACY_PARENT_ID_NAME);
        final String legacyPreviousSpanId = MDC.get(LEGACY_SPAN_ID_NAME);
        final String legacySpanExportable = MDC.get(LEGACY_EXPORTABLE_NAME);

        if (currentSpan != null) {

            String traceIdString = currentSpan.traceIdString();
            MDC.put("traceId", traceIdString);
            MDC.put(LEGACY_TRACE_ID_NAME, traceIdString);

            String parentId = currentSpan.parentId() == null ? null :
                    HexCodec.toLowerHex(currentSpan.parentId());
            replace("parentId", parentId);
            replace(LEGACY_PARENT_ID_NAME, parentId);

            String spanId = HexCodec.toLowerHex(currentSpan.spanId());
            MDC.put("spanId", spanId);
            MDC.put(LEGACY_SPAN_ID_NAME, spanId);

            String sampled = String.valueOf(currentSpan.sampled());
            MDC.put("spanExportable", sampled);
            MDC.put(LEGACY_EXPORTABLE_NAME, sampled);

            LOG.trace("Starting scope for span: {}", currentSpan);
            if (currentSpan.parentId() != null) {
                LOG.trace("With parent: {}", currentSpan.parentId());
            }
        } else {
            MDC.remove("traceId");
            MDC.remove("parentId");
            MDC.remove("spanId");
            MDC.remove("spanExportable");
            MDC.remove(LEGACY_TRACE_ID_NAME);
            MDC.remove(LEGACY_PARENT_ID_NAME);
            MDC.remove(LEGACY_SPAN_ID_NAME);
            MDC.remove(LEGACY_EXPORTABLE_NAME);
        }

        return () -> {
            if (currentSpan != null) {
                LOG.trace("Closing scope for span: {}", currentSpan);
            }
            scope.close();

            replace("traceId", previousTraceId);
            replace("parentId", previousParentId);
            replace("spanId", previousSpanId);
            replace("spanExportable", spanExportable);
            replace(LEGACY_TRACE_ID_NAME, legacyPreviousTraceId);
            replace(LEGACY_PARENT_ID_NAME, legacyPreviousParentId);
            replace(LEGACY_SPAN_ID_NAME, legacyPreviousSpanId);
            replace(LEGACY_EXPORTABLE_NAME, legacySpanExportable);
        };
    }

    private static void replace(String key, String value) {
        if (value == null) {
            MDC.remove(key);
        } else {
            MDC.put(key, value);
        }
    }
}
