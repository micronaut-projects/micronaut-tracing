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
package io.micronaut.tracing.opentelemetry.inspector;

import io.micronaut.core.annotation.Internal;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SpanProcessor;

/**
 * A {@link SpanProcessor} that records every ended span in the {@link DefaultTraceInspector}.
 *
 * @since 8.4.0
 */
@Internal
final class TraceInspectorSpanProcessor implements SpanProcessor {

    private final DefaultTraceInspector inspector;

    TraceInspectorSpanProcessor(DefaultTraceInspector inspector) {
        this.inspector = inspector;
    }

    @Override
    public void onStart(Context parentContext, ReadWriteSpan span) {
        // spans are recorded when they end
    }

    @Override
    public boolean isStartRequired() {
        return false;
    }

    @Override
    public void onEnd(ReadableSpan span) {
        inspector.record(span.toSpanData());
    }

    @Override
    public boolean isEndRequired() {
        return true;
    }
}
