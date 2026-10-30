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
package io.micronaut.tracing.opentelemetry.processing;

import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.tracing.opentelemetry.annotation.ScheduledSpan;
import org.jspecify.annotations.NonNull;

import java.util.Set;

/**
 * Adds {@link ScheduledSpan} to the {@code @Scheduled} methods, so that each run of the method by the scheduler
 * is traced. The methods that cannot be intercepted (private, static, final or declared by a final class, such
 * as a Kotlin class that is not open) are left untraced.
 *
 * @author Graeme Rocher
 * @since 8.4.0
 */
@Internal
public final class ScheduledSpanVisitor implements TypeElementVisitor<Object, Object> {

    private static final String SCHEDULED = "io.micronaut.scheduling.annotation.Scheduled";
    private static final String SCHEDULES = "io.micronaut.scheduling.annotation.Schedules";

    @Override
    public @NonNull VisitorKind getVisitorKind() {
        return VisitorKind.ISOLATING;
    }

    @Override
    public Set<String> getSupportedAnnotationNames() {
        return Set.of(SCHEDULED, SCHEDULES);
    }

    @Override
    public void visitMethod(MethodElement element, VisitorContext context) {
        if ((element.hasAnnotation(SCHEDULED) || element.hasAnnotation(SCHEDULES))
            && isInterceptable(element)) {
            element.annotate(ScheduledSpan.class);
        }
    }

    /**
     * Whether an around interceptor can be applied to the method by a subclass of its type.
     *
     * @param element the method
     * @return {@code true} if the method can be intercepted
     */
    static boolean isInterceptable(MethodElement element) {
        return !element.isStatic()
            && !element.isPrivate()
            && !element.isFinal()
            && !element.isAbstract()
            && !element.getOwningType().isFinal();
    }
}
