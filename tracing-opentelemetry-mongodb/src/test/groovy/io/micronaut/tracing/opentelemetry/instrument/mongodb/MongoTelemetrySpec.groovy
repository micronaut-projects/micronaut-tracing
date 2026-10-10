package io.micronaut.tracing.opentelemetry.instrument.mongodb

import com.mongodb.client.MongoClient
import com.mongodb.client.MongoCollection
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import io.micronaut.tracing.annotation.NewSpan
import io.micronaut.tracing.opentelemetry.test.TestSpans
import io.micronaut.tracing.util.Mongo
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.sdk.trace.data.SpanData
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.bson.Document
import reactor.core.publisher.Mono
import spock.lang.Specification

import static com.mongodb.client.model.Filters.eq

@MicronautTest
@Property(name = "spec.name", value = "MongoTelemetrySpec")
class MongoTelemetrySpec extends Specification implements TestPropertyProvider {

    static final AttributeKey<String> DB_SYSTEM_NAME = AttributeKey.stringKey("db.system.name")
    static final AttributeKey<String> DB_SYSTEM = AttributeKey.stringKey("db.system")
    static final AttributeKey<String> DB_OPERATION_NAME = AttributeKey.stringKey("db.operation.name")
    static final AttributeKey<String> DB_OPERATION = AttributeKey.stringKey("db.operation")
    static final AttributeKey<String> DB_COLLECTION_NAME = AttributeKey.stringKey("db.collection.name")
    static final AttributeKey<String> DB_MONGODB_COLLECTION = AttributeKey.stringKey("db.mongodb.collection")
    static final AttributeKey<String> DB_QUERY_TEXT = AttributeKey.stringKey("db.query.text")
    static final AttributeKey<String> DB_STATEMENT = AttributeKey.stringKey("db.statement")

    @Inject
    TestSpans spans

    @Inject
    @Client("/")
    HttpClient client

    @Inject
    MongoService mongoService

    @Inject
    com.mongodb.reactivestreams.client.MongoClient reactiveClient

    @Override
    Map<String, String> getProperties() {
        [
                'mongodb.uri'         : Mongo.uri,
                'otel.register.global': 'false',
                'otel.traces.exporter': 'none',
        ]
    }

    void setup() {
        spans.reset()
    }

    void "commands inside an HTTP request create client spans children of the server span"() {
        when:
        String value = client.toBlocking().retrieve("/mongo/item")

        then:
        value == "bar"

        when:
        SpanData server = awaitSpan(spans, SpanKind.SERVER, "GET /mongo/item")
        List<SpanData> mongo = spans.finishedSpans().findAll {
            it.kind == SpanKind.CLIENT && isMongo(it) && it.traceId == server.traceId
        }

        then:
        mongo*.name == ["insert test.http", "find test.http"]
        mongo.every { it.parentSpanId == server.spanId }
        mongo.every { collection(it) == "http" }
        mongo.collect { operation(it) } == ["insert", "find"]
    }

    void "a command inside @NewSpan creates a client span child of it with a sanitized statement"() {
        when:
        mongoService.store("s3cr3t")
        SpanData insert = awaitSpan(spans, "insert test.secrets")
        SpanData parent = spans.finishedSpans().find { it.name.endsWith("mongo-store") }

        then:
        insert.parentSpanId == parent.spanId
        isMongo(insert)
        statement(insert).contains('"secret"')
        !statement(insert).contains("s3cr3t")
    }

    void "commands of the reactive streams client are traced"() {
        when:
        Mono.from(reactiveClient.getDatabase("test").getCollection("reactive")
                .insertOne(new Document("name", "reactive"))).block()
        SpanData insert = awaitSpan(spans, "insert test.reactive")

        then:
        isMongo(insert)
        operation(insert) == "insert"
        collection(insert) == "reactive"
    }

    void "query sanitization can be disabled"() {
        given:
        ApplicationContext context = ApplicationContext.run([
                'mongodb.uri'                                          : Mongo.uri,
                'otel.instrumentation.mongo.query-sanitization-enabled': 'false',
                'otel.register.global'                                 : 'false',
                'otel.traces.exporter'                                 : 'none',
        ])
        TestSpans testSpans = context.getBean(TestSpans)

        when:
        context.getBean(MongoClient).getDatabase("test").getCollection("visible")
                .insertOne(new Document("secret", "visible-value"))
        SpanData insert = awaitSpan(testSpans, "insert test.visible")

        then:
        statement(insert).contains("visible-value")

        cleanup:
        context.close()
    }

    void "commands of a named server are traced"() {
        given:
        ApplicationContext context = ApplicationContext.run([
                'mongodb.servers.other.uri': Mongo.uri,
                'otel.register.global'     : 'false',
                'otel.traces.exporter'     : 'none',
        ])
        TestSpans testSpans = context.getBean(TestSpans)

        when:
        context.getBean(MongoClient, Qualifiers.byName("other")).getDatabase("test").getCollection("named")
                .insertOne(new Document("name", "named"))
        SpanData insert = awaitSpan(testSpans, "insert test.named")

        then:
        isMongo(insert)

        cleanup:
        context.close()
    }

    void "the instrumentation can be disabled"() {
        given:
        ApplicationContext context = ApplicationContext.run([
                'mongodb.uri'                       : Mongo.uri,
                'otel.instrumentation.mongo.enabled': 'false',
                'otel.register.global'              : 'false',
                'otel.traces.exporter'              : 'none',
        ])
        TestSpans testSpans = context.getBean(TestSpans)

        when:
        context.getBean(MongoClient).getDatabase("test").getCollection("disabled")
                .insertOne(new Document("name", "disabled"))

        then:
        !context.containsBean(MongoTelemetryConfiguration)
        !context.containsBean(MongoTracingClientSettingsBuilderCustomizer)
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

    private static boolean isMongo(SpanData span) {
        (span.attributes.get(DB_SYSTEM_NAME) ?: span.attributes.get(DB_SYSTEM)) == "mongodb"
    }

    private static String operation(SpanData span) {
        span.attributes.get(DB_OPERATION_NAME) ?: span.attributes.get(DB_OPERATION)
    }

    private static String collection(SpanData span) {
        span.attributes.get(DB_COLLECTION_NAME) ?: span.attributes.get(DB_MONGODB_COLLECTION)
    }

    private static String statement(SpanData span) {
        span.attributes.get(DB_QUERY_TEXT) ?: span.attributes.get(DB_STATEMENT)
    }

    @Requires(property = "spec.name", value = "MongoTelemetrySpec")
    @Controller("/mongo")
    static class MongoController {

        private final MongoClient mongoClient

        MongoController(MongoClient mongoClient) {
            this.mongoClient = mongoClient
        }

        @Get("/item")
        String item() {
            MongoCollection<Document> collection = mongoClient.getDatabase("test").getCollection("http")
            collection.insertOne(new Document("foo", "bar"))
            collection.find(eq("foo", "bar")).first().getString("foo")
        }
    }

    @Requires(property = "spec.name", value = "MongoTelemetrySpec")
    @Singleton
    static class MongoService {

        private final MongoClient mongoClient

        MongoService(MongoClient mongoClient) {
            this.mongoClient = mongoClient
        }

        @NewSpan("mongo-store")
        void store(String value) {
            mongoClient.getDatabase("test").getCollection("secrets").insertOne(new Document("secret", value))
        }
    }
}
