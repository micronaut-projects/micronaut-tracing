# Python Docs Disabled Test Inventory

This file tracks the Python documentation examples of Micronaut Tracing under `test-suite-python/src/test/python/micronaut/tracing/docs`
that are disabled, or that carry a workaround because the direct port of the Java example does not compile or does not behave
like the Java example yet (Python compiler gaps). It is the bug-fixing task list for the Python compiler
(`micronaut-inject-python` / `micronaut-context-python`); every row references a `TODO(python)` comment in the sources.

The Python examples are compiled by every build and their tests run with `./gradlew pythonCheck -Ppython-ci`
(the "Python CI" GitHub workflow).

## Reconciliation

- Last generated active `@Disabled` count: 1.
- Last generated command: `rg -n "@Disabled\(" test-suite-python/src/test/python`.
- Last full-suite command: `./gradlew :test-suite-python:test -Ppython-ci`.
- Last full-suite result: build successful, 5 tests executed (3 test classes), 1 skipped.

## Migration Rules

- The snippet classes live in `io.micronaut.tracing.docs` in every language: a Python source package cannot be the imported
  Java package `micronaut.tracing.opentelemetry` itself (the compiler generates its `__init__.py` for the imported
  `OpenTelemetryBuilderCustomizer`/`ResourceProvider` shims).
- OpenTelemetry types live in the `io.opentelemetry` package, which cannot be imported directly from Python yet (`io` is the
  standard library module): they are imported by name inside a `try:` block with an `except ImportError` fallback to the
  generated `opentelemetry...` shim packages.
- A Python test class is a `@MicronautTest` with `@Test` methods and plain `assert` statements; every test asserts on the
  spans of the injected `InMemorySpanExporter` or the metrics of the `InMemoryMetricReader`, so a missing interceptor or an
  ignored customizer fails the test instead of passing vacuously.
- Java functional interfaces (`OpenTelemetryBuilderCustomizer`, the meter provider customizer) are implemented with Python
  lambdas / nested functions returned from `@Factory` methods.

## Active `@Disabled` Tests

| Test | Reason |
| --- | --- |
| `HelloServiceTest.continue_span_adds_the_tag_of_the_nested_call_to_the_span` | `self.greet(...)` inside `HelloService.hello` invokes the Python method directly instead of the intercepted Java proxy of the bean, so the `@ContinueSpan` interceptor of the nested call never runs and the `hello.greeting` tag is missing (in Java the `this.greet(...)` call goes through the generated `$Intercepted` subclass). `continue_span_tags_the_current_span` proves the interceptor works when `greet` is invoked through the bean. The guide carries a `[.lang-python]` note. |

## Workarounds in the Sources

None.

## `java.type` usages

| Target | Reason |
| --- | --- |
| `AwsResourceProviderTest` (`AwsResourceProvider = java.type("micronaut.tracing.docs.AwsResourceProvider")`) | only a `java.type(...)` alias can be used as the runtime type of the `isinstance(self.resource_provider, AwsResourceProvider)` check (imported shim classes only work as type hints, generic bases and annotation members). |
