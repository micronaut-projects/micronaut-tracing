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
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.awaitility.Awaitility
import spock.lang.Specification

import java.util.concurrent.TimeUnit

@MicronautTest
class OpenTelemetryGrpcSpec extends Specification {

    @Inject
    TestBean testBean

    @Inject
    MyInterceptor myInterceptor

    @Inject
    InMemorySpanExporter exporter

    void "test hello world grpc"() {
        expect:
        testBean.sayHello("Fred") == "Hello Fred"
        Awaitility.await().atMost(5, TimeUnit.SECONDS).until(() -> exporter.getFinishedSpanItems().size() == 2)
        exporter.getFinishedSpanItems().size() == 2
        exporter.getFinishedSpanItems().kind.contains(io.opentelemetry.api.trace.SpanKind.SERVER)
        exporter.getFinishedSpanItems().kind.contains(io.opentelemetry.api.trace.SpanKind.CLIENT)
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
    static class MyInterceptor implements ServerInterceptor {

        boolean intercepted = false
        @Override
        <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
            intercepted = true
            return next.startCall(call, headers)
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
            responseObserver.onNext(reply)
            responseObserver.onCompleted()
        }
    }
}
