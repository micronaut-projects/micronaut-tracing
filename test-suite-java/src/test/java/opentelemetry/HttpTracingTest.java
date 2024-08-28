package opentelemetry;

import io.micronaut.context.annotation.Property;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.semconv.HttpAttributes;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static opentelemetry.HttpTracingTest.TRACING_ID;

@MicronautTest
@Property(name="otel.http.client.request-headers[0]", value = TRACING_ID)
@Property(name="otel.http.client.response-headers[0]", value = TRACING_ID)
@Property(name="otel.http.server.request-headers[0]", value = TRACING_ID)
@Property(name="otel.http.server.response-headers[0]", value = TRACING_ID)
public class HttpTracingTest {

    @Inject
    @Client("/")
    private HttpClient client;

    public static final String TRACING_ID = "X-TrackingId";
    public static final String TRACING_ID_IN_SPAN = HttpAttributes.HTTP_REQUEST_HEADER.getAttributeKey(TRACING_ID.toLowerCase()).getKey();

    @Inject
    ReactorHttpClient reactorHttpClient;

    @Inject
    private InMemorySpanExporter exporter;

    @Test
    void basicHTTPTracingTest() {
        // 1x Server POST 2x Server GET 2x Client GET, 1x Client POST, 3x Method call with NewSpan
        int clientSpanCount = 3;
        int serverSpanCount = 3;
        int internalSpanCount = 3;

        int spanNumbers = clientSpanCount + serverSpanCount + internalSpanCount;

        String tracingId = UUID.randomUUID().toString();
        HttpRequest<SomeBody> request = HttpRequest
            .POST("/annotations/enter", new SomeBody())
            .header(TRACING_ID, tracingId);


        client.toBlocking().exchange(request);

        Assertions.assertEquals(spanNumbers, exporter.getFinishedSpanItems().size());
        Assertions.assertEquals(internalSpanCount, exporter.getFinishedSpanItems().stream().filter(x -> x.getKind().equals(SpanKind.INTERNAL)).toList().size());
        Assertions.assertEquals(serverSpanCount, exporter.getFinishedSpanItems().stream().filter(x -> x.getKind().equals(SpanKind.SERVER)).toList().size());
        Assertions.assertEquals(clientSpanCount, exporter.getFinishedSpanItems().stream().filter(x -> x.getKind().equals(SpanKind.CLIENT)).toList().size());

        Assertions.assertEquals(serverSpanCount + clientSpanCount, exporter.getFinishedSpanItems().stream().map(SpanData::getAttributes).filter(x-> x.asMap().keySet().stream().anyMatch(y-> y.getKey().equals(TRACING_ID_IN_SPAN))).toList().size());

        Assertions.assertTrue(exporter.getFinishedSpanItems().stream().map(SpanData::getAttributes).anyMatch(x-> x.asMap().keySet().stream().anyMatch(y-> y.getKey().equals("tracing-annotation-span-attribute"))));
        Assertions.assertFalse(exporter.getFinishedSpanItems().stream().map(SpanData::getAttributes).anyMatch(x-> x.asMap().keySet().stream().anyMatch(y-> y.getKey().equals("tracing-annotation-span-tag-no-withspan"))));
        Assertions.assertTrue(exporter.getFinishedSpanItems().stream().map(SpanData::getAttributes).anyMatch(x-> x.asMap().keySet().stream().anyMatch(y-> y.getKey().equals("tracing-annotation-span-tag-with-withspan"))));
        Assertions.assertTrue(exporter.getFinishedSpanItems().stream().map(SpanData::getAttributes).anyMatch(x-> x.asMap().keySet().stream().anyMatch(y-> y.getKey().equals("privateMethodTestAttribute"))));
        Assertions.assertTrue(exporter.getFinishedSpanItems().stream().anyMatch(x -> x.getName().contains("#test-withspan-mapping")));
    }

}
