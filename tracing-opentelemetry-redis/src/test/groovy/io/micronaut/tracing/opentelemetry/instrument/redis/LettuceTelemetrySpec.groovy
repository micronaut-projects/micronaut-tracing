package io.micronaut.tracing.opentelemetry.instrument.redis

import io.lettuce.core.api.StatefulRedisConnection
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import io.micronaut.tracing.annotation.NewSpan
import io.micronaut.tracing.opentelemetry.test.TestSpans
import io.micronaut.tracing.util.Redis
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.sdk.trace.data.SpanData
import jakarta.inject.Inject
import jakarta.inject.Named
import jakarta.inject.Singleton
import spock.lang.Specification

@MicronautTest
@Property(name = "spec.name", value = "LettuceTelemetrySpec")
class LettuceTelemetrySpec extends Specification implements TestPropertyProvider {

    static final AttributeKey<String> DB_SYSTEM_NAME = AttributeKey.stringKey("db.system.name")
    static final AttributeKey<String> DB_SYSTEM = AttributeKey.stringKey("db.system")
    static final AttributeKey<String> DB_OPERATION_NAME = AttributeKey.stringKey("db.operation.name")
    static final AttributeKey<String> DB_OPERATION = AttributeKey.stringKey("db.operation")
    static final AttributeKey<String> DB_QUERY_TEXT = AttributeKey.stringKey("db.query.text")
    static final AttributeKey<String> DB_STATEMENT = AttributeKey.stringKey("db.statement")

    @Inject
    TestSpans spans

    @Inject
    @Client("/")
    HttpClient client

    @Inject
    RedisService redisService

    @Inject
    @Named("other")
    StatefulRedisConnection<String, String> otherConnection

    @Override
    Map<String, String> getProperties() {
        String uri = Redis.uri
        [
                'redis.uri'              : uri,
                'redis.servers.other.uri': uri,
                'otel.register.global'   : 'false',
                'otel.traces.exporter'   : 'none',
        ]
    }

    void "a command inside an HTTP request creates a client span child of the server span"() {
        when:
        String value = client.toBlocking().retrieve("/redis/value")

        then:
        value == "bar"

        when:
        SpanData server = awaitSpan(spans, SpanKind.SERVER, "GET /redis/value")
        // the connection handshake (HELLO, CLIENT SETINFO) runs on first use, in traces of its own
        List<SpanData> redis = spans.finishedSpans().findAll {
            it.kind == SpanKind.CLIENT && isRedis(it) && it.traceId == server.traceId
        }

        then:
        redis*.name == ["SET", "GET"]
        redis.every { it.parentSpanId == server.spanId }
        operation(redis.find { it.name == "SET" }) == "SET"
    }

    void "a command inside @NewSpan creates a client span child of it with a sanitized statement"() {
        when:
        redisService.store("key", "secret")
        SpanData parent = spans.awaitSpans(2).find { it.name.endsWith("redis-store") }
        SpanData set = spans.finishedSpans().find { it.kind == SpanKind.CLIENT && it.name == "SET" }

        then:
        parent != null
        set != null
        set.parentSpanId == parent.spanId
        isRedis(set)
        statement(set) == "SET key ?"
    }

    void "commands of a named server are traced"() {
        when:
        otherConnection.sync().set("named", "value")
        SpanData set = awaitSpan(spans, "SET")

        then:
        set.name == "SET"
        isRedis(set)
    }

    void "query sanitization can be disabled"() {
        given:
        ApplicationContext context = ApplicationContext.run([
                'redis.uri'                                         : Redis.uri,
                'otel.instrumentation.lettuce.query-sanitization-enabled': 'false',
                'otel.register.global'                              : 'false',
                'otel.traces.exporter'                              : 'none',
        ])
        TestSpans testSpans = context.getBean(TestSpans)

        when:
        context.getBean(StatefulRedisConnection).sync().set("key", "visible")
        SpanData set = awaitSpan(testSpans, "SET")

        then:
        statement(set) == "SET key visible"

        cleanup:
        context.close()
    }

    void "the instrumentation can be disabled"() {
        given:
        ApplicationContext context = ApplicationContext.run([
                'redis.uri'                            : Redis.uri,
                'otel.instrumentation.lettuce.enabled' : 'false',
                'otel.register.global'                 : 'false',
                'otel.traces.exporter'                 : 'none',
        ])
        TestSpans testSpans = context.getBean(TestSpans)

        when:
        context.getBean(StatefulRedisConnection).sync().set("key", "value")

        then:
        !context.containsBean(LettuceTelemetryConfiguration)
        !context.containsBean(LettuceTracingClientResourcesMutator)
        testSpans.finishedSpans().isEmpty()

        cleanup:
        context.close()
    }

    private static SpanData awaitSpan(TestSpans testSpans, String name) {
        awaitSpan(testSpans, SpanKind.CLIENT, name)
    }

    private static SpanData awaitSpan(TestSpans testSpans, SpanKind kind, String name) {
        long deadline = System.nanoTime() + TestSpans.DEFAULT_TIMEOUT.toNanos()
        while (System.nanoTime() < deadline) {
            SpanData span = testSpans.finishedSpans().find { it.kind == kind && it.name == name }
            if (span != null) {
                return span
            }
            Thread.sleep(50)
        }
        throw new AssertionError("No " + kind + " span named " + name + " in " + testSpans.finishedSpans())
    }

    private static boolean isRedis(SpanData span) {
        (span.attributes.get(DB_SYSTEM_NAME) ?: span.attributes.get(DB_SYSTEM)) == "redis"
    }

    private static String operation(SpanData span) {
        span.attributes.get(DB_OPERATION_NAME) ?: span.attributes.get(DB_OPERATION)
    }

    private static String statement(SpanData span) {
        span.attributes.get(DB_QUERY_TEXT) ?: span.attributes.get(DB_STATEMENT)
    }

    @Requires(property = "spec.name", value = "LettuceTelemetrySpec")
    @Controller("/redis")
    static class RedisController {

        private final StatefulRedisConnection<String, String> connection

        RedisController(StatefulRedisConnection<String, String> connection) {
            this.connection = connection
        }

        @Get("/value")
        String value() {
            connection.sync().set("foo", "bar")
            connection.sync().get("foo")
        }
    }

    @Requires(property = "spec.name", value = "LettuceTelemetrySpec")
    @Singleton
    static class RedisService {

        private final StatefulRedisConnection<String, String> connection

        RedisService(StatefulRedisConnection<String, String> connection) {
            this.connection = connection
        }

        @NewSpan("redis-store")
        void store(String key, String value) {
            connection.sync().set(key, value)
        }
    }
}
