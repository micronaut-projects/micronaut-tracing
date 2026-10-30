package tracing.ksp

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.tracing.annotation.NewSpan
import io.micronaut.tracing.annotation.SpanTag
import io.micronaut.tracing.util.MethodNameFormatter
import io.micronaut.tracing.util.TracedMethod
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SpanProcessor
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import jakarta.inject.Singleton
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * With KSP the method elements are named after the JVM methods, which Kotlin mangles for functions with
 * an inline class or kotlin.Result in their signature. The span names use the source names computed at
 * compile time.
 */
class KspSpanNameSpec {

    @Test
    fun `the JVM names of the functions are mangled`() {
        val jvmNames = KspTracedService::class.java.declaredMethods.map { it.name }.toSet()
        assertTrue(jvmNames.contains("result-d1pmJ48"), jvmNames.toString())
        assertTrue(jvmNames.contains("inlineParameter-GXKmHow"), jvmNames.toString())
        // a mangling hash made of lowercase letters only, the runtime heuristic keeps it
        assertTrue(jvmNames.contains("lowercaseHash-zqaolxw"), jvmNames.toString())
        assertEquals("lowercaseHash-zqaolxw", MethodNameFormatter.format("lowercaseHash-zqaolxw"))
    }

    @Test
    fun `the span data is computed at compile time with the source names`() {
        ApplicationContext.run(mapOf("spec.name" to "KspSpanNameSpec", "otel.traces.exporter" to "none")).use { ctx ->
            val definition = ctx.getBeanDefinition(KspTracedService::class.java)
            val methods = definition.executableMethods.associateBy { it.methodName }
            fun traced(jvmName: String) = TracedMethod.of(methods.getValue(jvmName))

            val result = traced("result-d1pmJ48")
            assertTrue(result.isPrecomputed)
            assertEquals("result", result.methodName)
            assertEquals("helloworld", traced("customResult-d1pmJ48").newSpanValue)
            assertEquals("customResult", traced("customResult-d1pmJ48").methodName)
            assertEquals("inlineParameter", traced("inlineParameter-GXKmHow").methodName)
            assertEquals("inlineReturn", traced("inlineReturn-CX3e0vc").methodName)
            val lowercase = traced("lowercaseHash-zqaolxw")
            assertEquals("lowercaseHash", lowercase.methodName)
            assertEquals(listOf(1), lowercase.tagIndexes.toList())
            assertEquals(listOf("name"), lowercase.tagNames.toList())
            assertEquals("plain", traced("plain").methodName)
            assertEquals("hyphen-name", traced("hyphen-name").methodName)
        }
    }

    @Test
    fun `spans are named after the source names`() {
        ApplicationContext.run(mapOf("spec.name" to "KspSpanNameSpec", "otel.traces.exporter" to "none")).use { ctx ->
            val service = ctx.getBean(KspTracedService::class.java)
            service.result()
            service.customResult()
            service.inlineParameter(Id("x"))
            service.inlineReturn()
            service.lowercaseHash(Key1383("k"), "tagged")
            service.plain()
            service.`hyphen-name`()

            val spans = ctx.getBean(InMemorySpanExporter::class.java).finishedSpanItems
            assertEquals(
                listOf(
                    "KspTracedService.result",
                    "KspTracedService.customResult#helloworld",
                    "KspTracedService.inlineParameter",
                    "KspTracedService.inlineReturn",
                    "KspTracedService.lowercaseHash",
                    "KspTracedService.plain",
                    "KspTracedService.hyphen-name"
                ),
                spans.map { it.name }
            )
            assertEquals("tagged", spans[4].attributes.get(AttributeKey.stringKey("name")))
        }
    }
}

@JvmInline
value class Id(val value: String)

// the fully qualified name of this class makes Kotlin mangle lowercaseHash with a lowercase-only hash
@JvmInline
value class Key1383(val value: String)

@Requires(property = "spec.name", value = "KspSpanNameSpec")
@Singleton
open class KspTracedService {

    @NewSpan
    open fun result(): Result<String> = Result.success("hello")

    @NewSpan("helloworld")
    open fun customResult(): Result<String> = Result.success("hello")

    @NewSpan
    open fun inlineParameter(id: Id): String = id.value

    @NewSpan
    open fun inlineReturn(): Id = Id("y")

    @NewSpan
    open fun lowercaseHash(key: Key1383, @SpanTag("name") name: String): String = key.value + name

    @NewSpan
    open fun plain(): String = "plain"

    @NewSpan
    open fun `hyphen-name`(): String = "hyphen"
}

@Factory
@Requires(property = "spec.name", value = "KspSpanNameSpec")
class KspSpanNameExporterFactory {

    @Singleton
    fun exporter(): InMemorySpanExporter = InMemorySpanExporter.create()

    @Singleton
    fun processor(exporter: InMemorySpanExporter): SpanProcessor = SimpleSpanProcessor.create(exporter)
}
