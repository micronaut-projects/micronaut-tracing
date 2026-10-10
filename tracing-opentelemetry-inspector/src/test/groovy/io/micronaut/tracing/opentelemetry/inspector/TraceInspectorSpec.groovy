package io.micronaut.tracing.opentelemetry.inspector

import io.micronaut.context.ApplicationContext
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanContext
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.TraceFlags
import io.opentelemetry.api.trace.TraceState
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context
import io.opentelemetry.context.Scope
import spock.lang.AutoCleanup
import spock.lang.Specification

import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit

class TraceInspectorSpec extends Specification {

    @AutoCleanup
    ApplicationContext context

    TraceInspector inspector
    Tracer tracer

    void start(Map<String, Object> properties = [:]) {
        context = ApplicationContext.run(['tracing.opentelemetry.inspector.enabled': true] + properties)
        inspector = context.getBean(TraceInspector)
        tracer = context.getBean(OpenTelemetry).getTracer("test")
    }

    void "a trace is completed when its local root span ends"() {
        given:
        start()

        when:
        Span root = tracer.spanBuilder("root").setSpanKind(SpanKind.SERVER).startSpan()
        Span child
        try (Scope ignored = root.makeCurrent()) {
            child = tracer.spanBuilder("child").setSpanKind(SpanKind.CLIENT).startSpan()
            child.end()
        }

        then: 'the child is pending'
        inspector.traces().isEmpty()

        when:
        root.end()
        List<TraceSummary> traces = inspector.traces()

        then:
        traces.size() == 1
        traces[0].traceId() == root.spanContext.traceId
        traces[0].name() == "root"
        traces[0].spanCount() == 2
        traces[0].droppedSpanCount() == 0
        !traces[0].error()
        traces[0].durationNanos() > 0

        when:
        InspectedTrace trace = inspector.trace(root.spanContext.traceId).get()

        then:
        trace.summary() == traces[0]
        trace.spans()*.name() == ["root", "child"]
        trace.spans()[0].parentSpanId() == null
        trace.spans()[0].kind() == "SERVER"
        trace.spans()[0].isLocalRoot()
        trace.spans()[1].parentSpanId() == root.spanContext.spanId
        trace.spans()[1].spanId() == child.spanContext.spanId
        trace.spans()[1].kind() == "CLIENT"
        trace.spans()[1].instrumentationScope() == "test"
        !inspector.trace("00000000000000000000000000000001").present
    }

    void "spans ending after the local root are added to the retained trace"() {
        given:
        start()

        when:
        Span root = tracer.spanBuilder("root").startSpan()
        Span late = tracer.spanBuilder("late").setParent(Context.root().with(root)).startSpan()
        root.end()
        late.end()

        then:
        inspector.traces().size() == 1
        inspector.traces()[0].spanCount() == 2
        inspector.trace(root.spanContext.traceId).get().spans()*.name() == ["root", "late"]
    }

    void "a span with a remote parent is a local root"() {
        given:
        start()
        SpanContext remote = SpanContext.createFromRemoteParent(
            "0af7651916cd43dd8448eb211c80319c", "b7ad6b7169203331", TraceFlags.sampled, TraceState.default)

        when:
        Span server = tracer.spanBuilder("server").setSpanKind(SpanKind.SERVER)
            .setParent(Context.root().with(Span.wrap(remote))).startSpan()
        server.end()

        then:
        inspector.traces()*.traceId() == ["0af7651916cd43dd8448eb211c80319c"]
        with(inspector.trace("0af7651916cd43dd8448eb211c80319c").get().spans()[0]) {
            parentSpanId() == "b7ad6b7169203331"
            remoteParent()
            isLocalRoot()
        }
    }

    void "the oldest completed traces are evicted"() {
        given:
        start('tracing.opentelemetry.inspector.max-traces': 3)

        when:
        List<String> ids = (1..5).collect {
            Span span = tracer.spanBuilder("trace-$it").startSpan()
            span.end()
            span.spanContext.traceId
        }

        then: 'newest first'
        inspector.traces()*.name() == ["trace-5", "trace-4", "trace-3"]
        !inspector.trace(ids[0]).present
        !inspector.trace(ids[1]).present
        inspector.trace(ids[4]).present
    }

    void "the oldest pending traces are discarded"() {
        given:
        start('tracing.opentelemetry.inspector.max-pending-traces': 2)

        when:
        List<Span> roots = (1..3).collect { tracer.spanBuilder("root-$it").startSpan() }
        roots.each { root -> tracer.spanBuilder("child").setParent(Context.root().with(root)).startSpan().end() }
        roots.each { it.end() }

        then:
        inspector.traces()*.name() == ["root-3", "root-2", "root-1"]
        inspector.traces()*.spanCount() == [2, 2, 1]
    }

    void "the number of spans per trace is capped"() {
        given:
        start('tracing.opentelemetry.inspector.max-spans-per-trace': 3)

        when:
        Span root = tracer.spanBuilder("root").startSpan()
        5.times { tracer.spanBuilder("child-$it").setParent(Context.root().with(root)).startSpan().end() }
        root.end()
        tracer.spanBuilder("late").setParent(Context.root().with(root)).startSpan().end()
        TraceSummary summary = inspector.traces()[0]

        then: 'the local root span is always kept'
        summary.name() == "root"
        summary.spanCount() == 4
        summary.droppedSpanCount() == 3
        inspector.trace(summary.traceId()).get().spans()*.name() == ["root", "child-0", "child-1", "child-2"]
    }

    void "string attribute values are truncated"() {
        given:
        start('tracing.opentelemetry.inspector.max-attribute-length': 5)

        when:
        Span root = tracer.spanBuilder("root")
            .setAttribute("text", "0123456789")
            .setAttribute("short", "abc")
            .setAttribute("number", 1234567890L)
            .setAttribute("flag", true)
            .setAttribute(AttributeKey.stringArrayKey("texts"), ["0123456789", "ab"])
            .startSpan()
        root.addEvent("event", Attributes.of(AttributeKey.stringKey("detail"), "abcdefgh"))
        root.recordException(new IllegalStateException("a long exception message"))
        root.setStatus(StatusCode.ERROR, "failed badly")
        root.end()
        InspectedSpan span = inspector.trace(root.spanContext.traceId).get().spans()[0]

        then:
        span.attributes() == [text: "01234", short: "abc", number: 1234567890L, flag: true, texts: ["01234", "ab"]]
        span.events()*.name() == ["event", "exception"]
        span.events()[0].attributes() == [detail: "abcde"]
        span.events()[1].attributes()["exception.type"] == "java."
        span.events()[1].attributes()["exception.message"] == "a lon"
        span.events()[1].attributes()["exception.stacktrace"] == "java."
        span.status() == "ERROR"
        span.statusDescription() == "faile"
        inspector.traces()[0].error()
    }

    void "links are recorded"() {
        given:
        start()
        Span linked = tracer.spanBuilder("linked").startSpan()
        linked.end()

        when:
        Span span = tracer.spanBuilder("consumer")
            .addLink(linked.spanContext, Attributes.of(AttributeKey.stringKey("reason"), "batch"))
            .startSpan()
        span.end()
        List<InspectedSpan.Link> links = inspector.trace(span.spanContext.traceId).get().spans()[0].links()

        then:
        links.size() == 1
        links[0].traceId() == linked.spanContext.traceId
        links[0].spanId() == linked.spanContext.spanId
        links[0].attributes() == [reason: "batch"]
    }

    void "traces are filtered by the query"() {
        given:
        start()
        Instant now = Instant.now()
        httpTrace("GET /hello", "/hello", 200, now.minusSeconds(60), Duration.ofMillis(5), false)
        httpTrace("GET /slow/{id}", "/slow/{id}", 200, now.minusSeconds(30), Duration.ofMillis(500), false)
        httpTrace("POST /fail", "/fail", 500, now.minusSeconds(10), Duration.ofMillis(20), true)

        expect:
        inspector.traces()*.name() == ["POST /fail", "GET /slow/{id}", "GET /hello"]
        inspector.traces(TraceQuery.builder().name("SLOW").build())*.name() == ["GET /slow/{id}"]
        inspector.traces(TraceQuery.builder().httpStatus(200).build())*.name() == ["GET /slow/{id}", "GET /hello"]
        inspector.traces(TraceQuery.builder().errorsOnly(true).build())*.name() == ["POST /fail"]
        inspector.traces(TraceQuery.builder().minDuration(Duration.ofMillis(100)).build())*.name() == ["GET /slow/{id}"]
        inspector.traces(TraceQuery.builder().since(now.minusSeconds(40)).build())*.name() == ["POST /fail", "GET /slow/{id}"]
        inspector.traces(TraceQuery.builder().limit(2).build())*.name() == ["POST /fail", "GET /slow/{id}"]
        inspector.traces(TraceQuery.builder().httpStatus(200).limit(1).build())*.name() == ["GET /slow/{id}"]

        and:
        with(inspector.traces(TraceQuery.builder().name("fail").build())[0]) {
            httpMethod() == "POST"
            httpRoute() == "/fail"
            httpStatus() == 500
            durationNanos() == Duration.ofMillis(20).toNanos()
            startEpochNanos() == TimeUnit.SECONDS.toNanos(now.minusSeconds(10).epochSecond) + now.nano
            error()
        }
    }

    void "clear discards all traces"() {
        given:
        start()
        tracer.spanBuilder("one").startSpan().end()
        Span root = tracer.spanBuilder("pending").startSpan()
        tracer.spanBuilder("child").setParent(Context.root().with(root)).startSpan().end()

        when:
        inspector.clear()
        root.end()

        then:
        inspector.traces()*.name() == ["pending"]
        inspector.traces()[0].spanCount() == 1
    }

    private void httpTrace(String name, String route, int status, Instant start, Duration duration, boolean error) {
        Span span = tracer.spanBuilder(name)
            .setSpanKind(SpanKind.SERVER)
            .setStartTimestamp(start)
            .setAttribute("http.request.method", name.split(" ")[0])
            .setAttribute("http.route", route)
            .setAttribute("url.path", route)
            .setAttribute("http.response.status_code", (long) status)
            .startSpan()
        if (error) {
            span.setStatus(StatusCode.ERROR)
        }
        span.end(start.plus(duration))
    }
}
