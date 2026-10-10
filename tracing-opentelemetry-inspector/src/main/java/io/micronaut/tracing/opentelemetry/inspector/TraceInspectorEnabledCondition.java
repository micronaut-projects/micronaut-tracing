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

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.BeanContext;
import io.micronaut.context.condition.Condition;
import io.micronaut.context.condition.ConditionContext;
import io.micronaut.context.env.Environment;
import io.micronaut.core.annotation.Internal;

import java.util.Optional;

/**
 * Enables the trace inspector when {@value TraceInspectorConfiguration#ENABLED} is {@code true}, or, when the
 * property is not set, when the {@value Environment#DEVELOPMENT} environment is active.
 *
 * @since 8.4.0
 */
@Internal
public final class TraceInspectorEnabledCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context) {
        Optional<Boolean> enabled = context.getProperty(TraceInspectorConfiguration.ENABLED, Boolean.class);
        if (enabled.isPresent()) {
            if (!enabled.get()) {
                context.fail("Trace inspector disabled with " + TraceInspectorConfiguration.ENABLED + "=false");
            }
            return enabled.get();
        }
        BeanContext beanContext = context.getBeanContext();
        if (beanContext instanceof ApplicationContext applicationContext
            && applicationContext.getEnvironment().getActiveNames().contains(Environment.DEVELOPMENT)) {
            return true;
        }
        context.fail("Trace inspector is only enabled by default in the '" + Environment.DEVELOPMENT
            + "' environment, set " + TraceInspectorConfiguration.ENABLED + "=true to enable it");
        return false;
    }
}
