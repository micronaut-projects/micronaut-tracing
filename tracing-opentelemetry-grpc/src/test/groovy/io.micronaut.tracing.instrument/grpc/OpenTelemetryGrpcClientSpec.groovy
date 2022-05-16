package io.micronaut.tracing.instrument.grpc

import io.grpc.Channel
import io.grpc.examples.helloworld.GreeterGrpc
import io.grpc.examples.helloworld.HelloReply
import io.grpc.examples.helloworld.HelloRequest
import io.grpc.stub.StreamObserver
import io.micronaut.context.annotation.Factory
import io.micronaut.grpc.annotation.GrpcChannel
import io.micronaut.grpc.server.GrpcServerChannel
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import jakarta.inject.Inject
import jakarta.inject.Singleton
import spock.lang.Specification

import static io.opentelemetry.api.trace.SpanKind.CLIENT
import static io.opentelemetry.api.trace.SpanKind.SERVER

@MicronautTest
class OpenTelemetryGrpcClientSpec extends Specification {

    @Inject
    TestBean testBean

    @Inject
    InMemorySpanExporter exporter

    void "test opentelemetry gRPC"() {
        expect:
        testBean.sayHello("Fred") == "Hello Fred"
        exporter.finishedSpanItems.size() == 2
        exporter.finishedSpanItems.kind.contains(SERVER)
        exporter.finishedSpanItems.kind.contains(CLIENT)

        cleanup:
        exporter.reset()
    }

    @Factory
    static class Clients {

        @Singleton
        GreeterGrpc.GreeterBlockingStub blockingStub(@GrpcChannel(GrpcServerChannel.NAME) Channel channel) {
            GreeterGrpc.newBlockingStub(channel)
        }
    }

    @Singleton
    static class TestBean {

        @Inject
        GreeterGrpc.GreeterBlockingStub blockingStub

        String sayHello(String message) {
            blockingStub.sayHello(
                    HelloRequest.newBuilder().setName(message).build()
            ).message
        }
    }

    @Singleton
    static class GreeterImpl extends GreeterGrpc.GreeterImplBase {
        @Override
        void sayHello(HelloRequest request, StreamObserver<HelloReply> responseObserver) {
            HelloReply reply = HelloReply.newBuilder().setMessage("Hello $request.name").build()
            responseObserver.onNext(reply)
            responseObserver.onCompleted()
        }
    }
}
