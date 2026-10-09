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

## After P2-3 (metrics gating)

P2-3 removes the `HttpClientMetrics` listener from the `@NewSpan` / `@ContinueSpan` (code) instrumenter and
only registers the HTTP server and client metrics listeners when OpenTelemetry exports metrics (see
`tracing.opentelemetry.http.{server,client}.metrics.enabled`). With the benchmark defaults
(`otel.metrics.exporter=none`) no metrics listener runs any more.

Both columns were measured on the same stack (OpenTelemetry 1.66), with the default short settings and
`-prof gc`; "before" is `stack/14-perf-benchmarks` (`10980f79`), "after" is `stack/15-metrics-gating`.

### `@NewSpan` (`NewSpanBenchmark`, B/op, error < ±15 B/op)

| method | `OTEL` before | `OTEL` after | Δ | `OTEL_NOOP_EXPORTER` before | after | Δ | `OTEL_RATIO_10` before | after | Δ |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| `sync` | 1,680 | 1,568 | -112 | 1,726 | 1,627 | -99 | 1,143 | 999 | -144 |
| `completionStage` | 1,720 | 1,608 | -112 | 1,790 | 1,680 | -110 | 1,223 | 1,071 | -152 |
| `mono` | 2,488 | 2,328 | -160 | 2,552 | 2,391 | -161 | 1,927 | 1,775 | -152 |
| `flux` | 2,392 | 2,232 | -160 | 2,455 | 2,295 | -160 | 1,855 | 1,679 | -176 |

`NONE` stays at 0 B/op. An unsampled synchronous call (most of D) now allocates about 1 KB.

### HTTP server request (`HttpServerBenchmark.request`, B/op, mean of 3 runs)

| mode | before | after | Δ |
|---|---:|---:|---:|
| `NONE` (A) | 18,192 | 17,182 | (-1,010, noise) |
| `OTEL` (B) | 27,687 | 27,555 | -132 |
| `OTEL_NOOP_EXPORTER` (C) | 27,944 | 27,495 | -449 |
| `OTEL_RATIO_10` (D) | 27,848 | 26,626 | -1,222 |

The machine was busy and the per-run error of this benchmark was ±1-4 KB/op (even `NONE`, which this change
does not touch, moved by 1 KB), so the HTTP saving is below the resolution of these runs. The removed server
listener state, `Context` copy and attribute merge account for a few hundred bytes per request; the
benchmark client is not instrumented, so the client listener saving is not measured here.
