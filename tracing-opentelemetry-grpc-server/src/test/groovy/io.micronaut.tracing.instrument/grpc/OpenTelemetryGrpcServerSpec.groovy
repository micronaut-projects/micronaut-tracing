package io.micronaut.tracing.instrument.grpc

import io.grpc.Channel
import io.grpc.Metadata
import io.grpc.ServerCall
import io.grpc.ServerCallHandler
import io.grpc.ServerInterceptor
import io.grpc.examples.helloworld.GreeterGrpc
import io.grpc.examples.helloworld.HelloReply
import io.grpc.examples.helloworld.HelloRequest
import io.grpc.stub.StreamObserver
import io.micronaut.context.annotation.Factory
import io.micronaut.grpc.annotation.GrpcChannel
import io.micronaut.grpc.server.GrpcServerChannel
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.tracing.annotation.NewSpan
import io.micronaut.tracing.opentelemetry.interceptor.TraceInterceptor
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import jakarta.inject.Inject
import jakarta.inject.Singleton
import spock.lang.Specification

@MicronautTest
class OpenTelemetryGrpcServerSpec extends Specification {

    @Inject
    TestBean testBean

    @Inject
    InMemorySpanExporter exporter

    void "test opentelemetry grpc server"() {
        expect:
        testBean.sayHello("Fred") == "Hello Fred"
        exporter.getFinishedSpanItems().size() == 1
        exporter.getFinishedSpanItems().kind.contains(io.opentelemetry.api.trace.SpanKind.SERVER)
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
            HelloReply reply = HelloReply.newBuilder().setMessage("Hello " + request.getName()).build();
            testNewSpan()
            responseObserver.onNext(reply)
            responseObserver.onCompleted()
        }

        @NewSpan
        void testNewSpan() {
        }
    }
}
