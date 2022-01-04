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
package io.micronaut.tracing.instrument.util;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.scheduling.instrument.InvocationInstrumenter;

/**
 * A factory interface for tracing invocation instrumentation.
 * The factory method decides if instrumentation is needed.
 *
 * @author Denis Stepanov
 * @since 1.3
 */
@Experimental
public interface TracingInvocationInstrumenterFactory {

    /**
     * An optional instrumentation.
     *
     * @return an instrumentation, or null if none is necessary
     */
    @Nullable
    InvocationInstrumenter newTracingInvocationInstrumenter();
}
