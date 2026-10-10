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
package io.micronaut.tracing.processing;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.StringUtils;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.tracing.annotation.ContinueSpan;
import io.micronaut.tracing.annotation.NewSpan;
import io.micronaut.tracing.annotation.SpanTag;
import io.micronaut.tracing.util.MethodNameFormatter;
import io.micronaut.tracing.util.SpanMetadata;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.Set;

/**
 * Computes the span data of the {@link NewSpan} and {@link ContinueSpan} methods (and of the methods
 * mapped to them, like OpenTelemetry {@code @WithSpan} and {@code @AddingSpanAttributes}) at compile
 * time and stores it in {@link SpanMetadata}: the method name used in the span name and the
 * {@link SpanTag} parameters.
 * It also stores the source name of the Kotlin HTTP route methods with a mangled JVM name, used for
 * the {@code code.function.name} attribute of the HTTP server spans.
 *
 * <p>The Kotlin (KSP) method elements are named after the JVM method, which is mangled for functions
 * with an inline class or {@code kotlin.Result} in their signature ({@code name-hash}). The visitor uses
 * the source name of such a function, which the runtime can only guess from the JVM name.</p>
 *
 * @since 8.4.0
 */
@Internal
public final class SpanMetadataVisitor implements TypeElementVisitor<Object, Object> {

    private static final String WITH_SPAN = "io.opentelemetry.instrumentation.annotations.WithSpan";
    private static final String ADDING_SPAN_ATTRIBUTES = "io.opentelemetry.instrumentation.annotations.AddingSpanAttributes";
    private static final String CONTROLLER = "io.micronaut.http.annotation.Controller";
    private static final String HTTP_METHOD_MAPPING = "io.micronaut.http.annotation.HttpMethodMapping";
    private static final String KSP_DECLARATION = "com.google.devtools.ksp.symbol.KSDeclaration";
    private static final String KSP_NAME = "com.google.devtools.ksp.symbol.KSName";

    @Override
    public @NonNull VisitorKind getVisitorKind() {
        return VisitorKind.ISOLATING;
    }

    @Override
    public Set<String> getSupportedAnnotationNames() {
        return Set.of(NewSpan.class.getName(), ContinueSpan.class.getName(), WITH_SPAN, ADDING_SPAN_ATTRIBUTES,
            CONTROLLER, HTTP_METHOD_MAPPING);
    }

    @Override
    public void visitMethod(MethodElement element, VisitorContext context) {
        if (element.isStatic() || element.isPrivate()) {
            return;
        }
        if (!(element.hasAnnotation(NewSpan.class) || element.hasAnnotation(ContinueSpan.class))) {
            visitRouteMethod(element, context);
            return;
        }
        String methodName = methodName(element, context);
        ParameterElement[] parameters = element.getParameters();
        int count = 0;
        for (ParameterElement parameter : parameters) {
            if (parameter.hasAnnotation(SpanTag.class)) {
                count++;
            }
        }
        int[] tagIndexes = new int[count];
        String[] tagNames = new String[count];
        int tag = 0;
        for (int i = 0; i < parameters.length; i++) {
            ParameterElement parameter = parameters[i];
            if (parameter.hasAnnotation(SpanTag.class)) {
                tagIndexes[tag] = i;
                tagNames[tag] = parameter.stringValue(SpanTag.class).filter(StringUtils::isNotEmpty).orElse(parameter.getName());
                tag++;
            }
        }
        element.annotate(SpanMetadata.class, builder -> {
            builder.member(SpanMetadata.MEMBER_METHOD, methodName);
            if (tagIndexes.length > 0) {
                builder.member(SpanMetadata.MEMBER_TAG_INDEXES, tagIndexes);
                builder.member(SpanMetadata.MEMBER_TAG_NAMES, tagNames);
            }
        });
    }

    /**
     * Stores the source name of a Kotlin HTTP route method whose JVM name contains a {@code -}, used for
     * the {@code code.function.name} attribute of the HTTP server spans. The name of any other route
     * method is its JVM name, which needs no metadata.
     *
     * @param element the method element
     * @param context the visitor context
     */
    private static void visitRouteMethod(MethodElement element, VisitorContext context) {
        if (context.getLanguage() != VisitorContext.Language.KOTLIN
            || element.getName().indexOf('-') < 0
            || !element.hasStereotype(HTTP_METHOD_MAPPING)) {
            return;
        }
        String methodName = methodName(element, context);
        element.annotate(SpanMetadata.class, builder -> builder.member(SpanMetadata.MEMBER_METHOD, methodName));
    }

    private static String methodName(MethodElement element, VisitorContext context) {
        String jvmName = element.getName();
        if (context.getLanguage() != VisitorContext.Language.KOTLIN || jvmName.indexOf('-') < 0) {
            return jvmName;
        }
        String sourceName = kotlinSourceName(element);
        if (sourceName == null) {
            return MethodNameFormatter.format(jvmName);
        }
        // only strip the mangling suffix: a function renamed with @JvmName keeps its JVM name, as at runtime
        return jvmName.startsWith(sourceName + '-') ? sourceName : jvmName;
    }

    /**
     * Reads the source name of a KSP function declaration, without a compile dependency on KSP.
     *
     * @param element the method element
     * @return the name, or {@code null} if it cannot be read
     */
    private static @Nullable String kotlinSourceName(MethodElement element) {
        try {
            Object nativeElement = element.getNativeType();
            Object declaration = nativeElement.getClass().getMethod("getDeclaration").invoke(nativeElement);
            ClassLoader classLoader = declaration.getClass().getClassLoader();
            Class<?> declarationType = Class.forName(KSP_DECLARATION, false, classLoader);
            if (!declarationType.isInstance(declaration)) {
                return null;
            }
            Object name = declarationType.getMethod("getSimpleName").invoke(declaration);
            Method asString = Class.forName(KSP_NAME, false, classLoader).getMethod("asString");
            Object value = asString.invoke(name);
            return value instanceof String s && !s.isEmpty() ? s : null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            return null;
        }
    }
}
