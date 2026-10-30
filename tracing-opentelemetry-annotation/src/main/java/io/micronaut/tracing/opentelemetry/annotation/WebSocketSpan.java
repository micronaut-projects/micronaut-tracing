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
package io.micronaut.tracing.opentelemetry.annotation;

import io.micronaut.aop.InterceptorBinding;
import io.micronaut.aop.InterceptorKind;
import io.micronaut.core.annotation.Internal;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Binds the interceptor that traces the invocations of the {@code @OnOpen}, {@code @OnMessage},
 * {@code @OnClose} and {@code @OnError} methods of a {@code @ServerWebSocket} or {@code @ClientWebSocket}.
 *
 * <p>Added at compile time by the {@code micronaut-tracing-opentelemetry-annotation} processor to the
 * WebSocket handler methods that can be intercepted (neither private, static, final nor declared by a final
 * class). Not meant to be used directly.</p>
 *
 * @author Graeme Rocher
 * @since 8.4.0
 */
@Internal
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
@InterceptorBinding(kind = InterceptorKind.AROUND)
public @interface WebSocketSpan {
}
