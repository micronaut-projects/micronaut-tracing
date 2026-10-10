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
import org.jspecify.annotations.NonNull;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.tracing.opentelemetry.annotation.WebSocketSpan;

import java.util.Set;

/**
 * Adds {@link WebSocketSpan} to the {@code @OnOpen}, {@code @OnMessage}, {@code @OnClose} and {@code @OnError}
 * methods of the {@code @ServerWebSocket} and {@code @ClientWebSocket} classes, so that each invocation of a
 * handler is traced. The methods that cannot be intercepted (private, static, final or declared by a final
 * class, such as a Kotlin class that is not open) are left untraced.
 *
 * @author Graeme Rocher
 * @since 8.4.0
 */
@Internal
public final class WebSocketSpanVisitor implements TypeElementVisitor<Object, Object> {

    private static final String WEBSOCKET_COMPONENT = "io.micronaut.websocket.annotation.WebSocketComponent";
    private static final String PACKAGE = "io.micronaut.websocket.annotation.";
    private static final Set<String> HANDLERS = Set.of(
        PACKAGE + "OnOpen",
        PACKAGE + "OnMessage",
        PACKAGE + "OnClose",
        PACKAGE + "OnError"
    );

    @Override
    public @NonNull VisitorKind getVisitorKind() {
        return VisitorKind.ISOLATING;
    }

    @Override
    public Set<String> getSupportedAnnotationNames() {
        return HANDLERS;
    }

    @Override
    public void visitMethod(MethodElement element, VisitorContext context) {
        if (!element.getOwningType().hasStereotype(WEBSOCKET_COMPONENT)
            || !ScheduledSpanVisitor.isInterceptable(element)) {
            return;
        }
        for (String handler : HANDLERS) {
            if (element.hasAnnotation(handler)) {
                element.annotate(WebSocketSpan.class);
                return;
            }
        }
    }
}
