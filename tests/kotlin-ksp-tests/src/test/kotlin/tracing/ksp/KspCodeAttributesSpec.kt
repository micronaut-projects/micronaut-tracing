package tracing.ksp

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.tracing.util.SpanMetadata
import io.micronaut.tracing.util.TracedMethod
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.instrumentation.annotations.AddingSpanAttributes
import io.opentelemetry.instrumentation.annotations.SpanAttribute
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SpanProcessor
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import io.opentelemetry.semconv.CodeAttributes
import jakarta.inject.Singleton
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The code.function.name attribute of the HTTP server spans uses the source names of the Kotlin route
 * functions, computed at compile time, and @AddingSpanAttributes adds attributes to the current span.
 */
class KspCodeAttributesSpec {

    private val properties = mapOf<String, Any>(
        "spec.name" to "KspCodeAttributesSpec",
        "otel.register.global" to false,
        "micronaut.application.name" to "ksp-code-attributes"
    )

    @Test
    fun `the source names of the mangled route functions are computed at compile time`() {
        val jvmNames = KspCodeController::class.java.declaredMethods.map { it.name }.toSet()
        val mangled = jvmNames.single { it.startsWith("inlineReturn-") }

        ApplicationContext.run(properties).use { ctx ->
            val methods = ctx.getBeanDefinition(KspCodeController::class.java).executableMethods.associateBy { it.methodName }
            val inlineReturn = methods.getValue(mangled)
            assertEquals("inlineReturn", inlineReturn.stringValue(SpanMetadata::class.java, SpanMetadata.MEMBER_METHOD).orElse(null))
            assertEquals("inlineReturn", TracedMethod.methodName(inlineReturn))
            assertEquals("hyphen-name", TracedMethod.methodName(methods.getValue("hyphen-name")))
            // the name of a plain route function needs no metadata
            assertFalse(methods.getValue("plain").hasAnnotation(SpanMetadata::class.java))
        }
    }

    @Test
    fun `the server spans of Kotlin routes have code function name with the source names`() {
        ApplicationContext.run(EmbeddedServer::class.java, properties).use { server ->
            HttpClient.create(server.url).use { client ->
                val blocking = client.toBlocking()
                assertEquals("plain", blocking.retrieve("/ksp-code/plain"))
                assertEquals("hyphen", blocking.retrieve("/ksp-code/hyphen"))
                runCatching { blocking.retrieve("/ksp-code/inline") }

                val exporter = server.applicationContext.getBean(InMemorySpanExporter::class.java)
                val expected = setOf(
                    "tracing.ksp.KspCodeController.plain",
                    "tracing.ksp.KspCodeController.hyphen-name",
                    "tracing.ksp.KspCodeController.inlineReturn"
                )
                var functionNames: Set<String?> = emptySet()
                val deadline = System.currentTimeMillis() + 10_000
                while (System.currentTimeMillis() < deadline) {
                    functionNames = exporter.finishedSpanItems
                        .filter { it.kind == SpanKind.SERVER }
                        .map { it.attributes.get(CodeAttributes.CODE_FUNCTION_NAME) }
                        .toSet()
                    if (functionNames == expected) {
                        break
                    }
                    Thread.sleep(50)
                }
                assertEquals(expected, functionNames)
            }
        }
    }

    @Test
    fun `AddingSpanAttributes adds the span attributes to the current span`() {
        ApplicationContext.run(properties).use { ctx ->
            val service = ctx.getBean(KspAddingSpanAttributesService::class.java)
            val tracer = ctx.getBean(Tracer::class.java)
            val parent: Span = tracer.spanBuilder("parent").startSpan()
            parent.makeCurrent().use {
                assertEquals("k1", service.add("k", 1))
            }
            parent.end()

            val spans = ctx.getBean(InMemorySpanExporter::class.java).finishedSpanItems
            assertEquals(listOf("parent"), spans.map { it.name })
            assertEquals("k", spans[0].attributes.get(AttributeKey.stringKey("kotlin.attribute")))
            assertNull(spans[0].attributes.get(AttributeKey.stringKey("number")))
        }
    }
}

@Requires(property = "spec.name", value = "KspCodeAttributesSpec")
@Controller("/ksp-code", produces = [MediaType.TEXT_PLAIN])
open class KspCodeController {

    @Get("/plain")
    open fun plain(): String = "plain"

    @Get("/hyphen")
    open fun `hyphen-name`(): String = "hyphen"

    @Get("/inline")
    open fun inlineReturn(): Id = Id("y")
}

@Requires(property = "spec.name", value = "KspCodeAttributesSpec")
@Singleton
open class KspAddingSpanAttributesService {

    @AddingSpanAttributes
    open fun add(@SpanAttribute("kotlin.attribute") key: String, number: Int): String = key + number
}

@Factory
@Requires(property = "spec.name", value = "KspCodeAttributesSpec")
class KspCodeAttributesExporterFactory {

    @Singleton
    fun exporter(): InMemorySpanExporter = InMemorySpanExporter.create()

    @Singleton
    fun processor(exporter: InMemorySpanExporter): SpanProcessor = SimpleSpanProcessor.create(exporter)
}
