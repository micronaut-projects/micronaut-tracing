package tracing

import io.micronaut.tracing.annotation.NewSpan
import io.micronaut.tracing.util.MethodNameFormatter
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class ResultSpanNameSpec {

    @Test
    fun `formatter strips Kotlin Result mangling from method names`() {
        val methodNames = ResultSpanService::class.java.declaredMethods.map { it.name }.toSet()
        val customMethodNames = CustomResultSpanService::class.java.declaredMethods.map { it.name }.toSet()
        val defaultMethod = methodNames.single { it.startsWith("defaultSpan-") }
        val customMethod = customMethodNames.single { it.startsWith("customSpan-") }
        val backtickMethod = methodNames.single { it == "hyphen-name" }

        Assertions.assertNotEquals("defaultSpan", defaultMethod)
        Assertions.assertNotEquals("customSpan", customMethod)
        Assertions.assertEquals("defaultSpan", MethodNameFormatter.format(defaultMethod))
        Assertions.assertEquals("customSpan", MethodNameFormatter.format(customMethod))
        Assertions.assertEquals("customSpan#helloworld", MethodNameFormatter.format(customMethod) + "#helloworld")
        Assertions.assertEquals("defaultSpan", MethodNameFormatter.format("defaultSpan-longerHash"))
        Assertions.assertEquals("defaultSpan", MethodNameFormatter.format("defaultSpan-longerHash\$default"))
        Assertions.assertEquals("defaultSpan", MethodNameFormatter.format("defaultSpan-longer-Hash\$default"))
        Assertions.assertEquals("defaultSpan-", MethodNameFormatter.format("defaultSpan-"))
        Assertions.assertEquals("defaultSpan-\$default", MethodNameFormatter.format("defaultSpan-\$default"))
        Assertions.assertEquals("defaultSpan-short", MethodNameFormatter.format("defaultSpan-short"))
        Assertions.assertEquals("defaultSpan-hash\$other", MethodNameFormatter.format("defaultSpan-hash\$other"))
        Assertions.assertEquals("plainMethod", MethodNameFormatter.format("plainMethod"))
        Assertions.assertEquals("hyphen-name", MethodNameFormatter.format(backtickMethod))
    }
}

open class ResultSpanService {

    @NewSpan
    open fun defaultSpan(): Result<String> = Result.success("hello")

    open fun `hyphen-name`(): String = "hello"
}

open class CustomResultSpanService {

    @NewSpan("helloworld")
    open fun customSpan(): Result<String> = Result.success("hello")
}
