# Micronaut Tracing benchmarks

JMH benchmarks measuring the overhead of the tracing integrations. This project is **not published**: it
applies neither the module nor the publishing conventions, so it is not part of the BOM, japicmp, the
aggregated javadoc or a release. `./gradlew check` only compiles it (`jmhClasses`), it never runs it.

The benchmarks live in `src/jmh/java` and use the [JMH Gradle plugin](https://github.com/melix/jmh-gradle-plugin).

## Benchmarks

| Benchmark | What it measures |
|---|---|
| `HttpServerBenchmark.request` | A `GET /bench/hello/{name}` through the full Micronaut HTTP server filter chain (embedded Netty, random port), sent by the JDK `HttpClient` over a kept-alive HTTP/1.1 connection. The client is not instrumented. |
| `NewSpanBenchmark.{sync,completionStage,mono,flux}` | A call of a `@NewSpan` method (default span name, one `@SpanTag` argument) without a parent span, so each call starts a root span. Reactive results are consumed (`block()` / `blockLast()`), the `Flux` has 3 elements. |
| `ExclusionBenchmark.{tracedPath,excludedPath}` | The `otel.exclusions` predicate run per request by the HTTP filters, with 3 patterns, for a path that is not excluded (the common case) and one matching the last pattern. |
| `HttpServerAttributesBenchmark.httpRoute` | One `http.route` lookup of the (package-private) `MicronautHttpServerAttributesGetter` for a matched route. The instrumenter resolves it several times per request. |

The HTTP server and `@NewSpan` benchmarks start one `ApplicationContext` per trial and take a `mode`
parameter:

| `mode` | Configuration |
|---|---|
| `NONE` (A) | `micronaut.otel.enabled=false`: no OpenTelemetry bean, filter or interceptor. Baseline. |
| `OTEL` (B) | Micronaut defaults: every exporter `none` (traces, metrics, logs), default sampler `parentbased_always_on`. Spans are recorded and dropped, the HTTP metrics listeners are still registered. |
| `OTEL_NOOP_EXPORTER` (C) | B plus a `BatchSpanProcessor` with a no-op exporter (100% sampling, realistic span pipeline without I/O). |
| `OTEL_RATIO_10` (D) | B with `otel.traces.sampler=parentbased_traceidratio` and `otel.traces.sampler.arg=0.1`. |

## Running

```bash
# everything, short defaults: 1 fork, 3 x 2s warmup, 5 x 2s measurement
./gradlew :benchmarks:jmh

# a subset (regular expressions, comma separated) with allocation profiling
./gradlew :benchmarks:jmh -Pjmh.includes=HttpServerBenchmark -Pjmh.profilers=gc

# restrict a parameter (values separated by |, parameters by ;)
./gradlew :benchmarks:jmh -Pjmh.includes=NewSpanBenchmark.mono -Pjmh.params='mode=NONE|OTEL' -Pjmh.profilers=gc

# longer runs
./gradlew :benchmarks:jmh -Pjmh.fork=3 -Pjmh.warmupIterations=5 -Pjmh.iterations=10 -Pjmh.warmup=5s -Pjmh.timeOnIteration=5s
```

Results are written to `benchmarks/build/results/jmh/results.json`. Compare `gc.alloc.rate.norm` (bytes
allocated per operation): it is far more stable than throughput on a busy machine. For
`HttpServerBenchmark` it counts every thread of the JVM, so it includes the JDK client and the Netty event
loop; the client part is a constant offset across the modes.

## Baseline

Measured on the `8.4.x` stack (Micronaut Core 5.3.0-SNAPSHOT, OpenTelemetry 1.64.0 / instrumentation
2.30.0) before any of the performance fixes, with the defaults above and `-prof gc`. Machine: Apple M2 Max
(12 cores, 64 GB), macOS, Oracle GraalVM 25.0.4 (HotSpot JIT, JVMCI enabled), JMH 1.37. The machine
was shared with other builds while running, so the **throughput numbers are indicative only** (see the
error columns in `results.json`, the HTTP ones are around ±50-150%); the allocation numbers are stable
(±0.5%).

### HTTP server request (`HttpServerBenchmark.request`)

| mode | ops/s | B/op | B/op vs `NONE` |
|---|---:|---:|---:|
| `NONE` (A) | 11,464 | 17,040 | - |
| `OTEL` (B) | 11,341 | 26,303 | +9,263 |
| `OTEL_NOOP_EXPORTER` (C) | 9,604 | 26,393 | +9,353 |
| `OTEL_RATIO_10` (D) | 10,367 | 26,094 | +9,054 |

Tracing adds about 9 KB per request. 90% of the requests are not sampled in D, yet it still allocates
almost as much as B: the metrics listeners, the attribute extraction and the filter's reactive wrapping
run for every request, sampled or not.

### `@NewSpan` (`NewSpanBenchmark`)

| method | `NONE` ops/s | `NONE` B/op | `OTEL` ops/s | `OTEL` B/op | `OTEL_NOOP_EXPORTER` ops/s | `OTEL_NOOP_EXPORTER` B/op | `OTEL_RATIO_10` ops/s | `OTEL_RATIO_10` B/op |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| `sync` | 198M | 0 | 2.64M | 1,416 | 2.11M | 1,479 | 2.72M | 1,101 |
| `completionStage` | 136M | 0 | 0.23M | 1,456 | 1.43M | 1,516 | 2.14M | 1,157 |
| `mono` | 189M | 0 | 1.73M | 2,224 | 1.80M | 2,264 | 2.17M | 1,925 |
| `flux` | 151M | 0 | 1.63M | 2,128 | 1.22M | 2,288 | 1.62M | 1,829 |

Without tracing the interceptor chain is empty and the call costs nothing measurable. With tracing every call costs 1.1-2.3 KB, and an
unsampled call (most of D) still allocates about 1.1 KB (sync) to 1.9 KB (`Mono`), the reactive variants
about 800 B more than the synchronous one.

### Micro-benchmarks

| benchmark | ops/s | B/op |
|---|---:|---:|
| `ExclusionBenchmark.tracedPath` | 38.5M | 168 |
| `ExclusionBenchmark.excludedPath` | 25.9M | 168 |
| `HttpServerAttributesBenchmark.httpRoute` | 11.3M | 328 |

The exclusion check allocates a stream pipeline on every call, and each `http.route` lookup allocates
328 bytes of `Optional`s and the route string.
