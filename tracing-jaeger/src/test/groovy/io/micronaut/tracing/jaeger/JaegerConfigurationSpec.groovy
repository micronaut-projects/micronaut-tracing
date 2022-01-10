package io.micronaut.tracing.jaeger

import io.jaegertracing.Configuration
import io.jaegertracing.Configuration.CodecConfiguration
import io.jaegertracing.internal.propagation.B3TextMapCodec
import io.jaegertracing.internal.propagation.BinaryCodec
import io.jaegertracing.internal.propagation.TextMapCodec
import io.jaegertracing.internal.propagation.TraceContextCodec
import io.jaegertracing.spi.Codec
import io.micronaut.context.ApplicationContext
import io.opentracing.propagation.Binary
import io.opentracing.propagation.Format
import io.opentracing.propagation.TextMap
import spock.lang.AutoCleanup
import spock.lang.Specification

import static io.opentracing.propagation.Format.Builtin.BINARY
import static io.opentracing.propagation.Format.Builtin.HTTP_HEADERS
import static io.opentracing.propagation.Format.Builtin.TEXT_MAP

class JaegerConfigurationSpec extends Specification {

    @AutoCleanup
    ApplicationContext ctx

    void 'test reporter configuration'() {
        given:
        run('tracing.jaeger.sender.agentHost': 'foo', 'tracing.jaeger.sender.agentPort': 9999)

        when:
        Configuration config = ctx.getBean(JaegerConfiguration).configuration

        then:
        config.reporter.senderConfiguration.agentHost == 'foo'
        config.reporter.senderConfiguration.agentPort == 9999
    }

    void 'test codec configuration W3C'() {
        given:
        run('tracing.jaeger.codecs': 'W3C')

        when:
        CodecConfiguration config = ctx.getBean(JaegerConfiguration).configuration.codec
        Map<Format<?>, List<Codec<TextMap>>> codecs = config.codecs

        then:
        codecs.size() == 2

        when:
        List<Codec<TextMap>> headerCodecs = codecs[HTTP_HEADERS]

        then:
        headerCodecs.size() == 1
        headerCodecs[0] instanceof TraceContextCodec

        when:
        List<Codec<TextMap>> textMapCodecs = codecs[TEXT_MAP]

        then:
        textMapCodecs.size() == 1
        textMapCodecs[0] instanceof TraceContextCodec
    }

    void 'test codec configuration W3C B3'() {
        given:
        run('tracing.jaeger.codecs': 'W3C,B3')

        when:
        CodecConfiguration config = ctx.getBean(JaegerConfiguration).configuration.codec
        Map<Format<?>, List<Codec<TextMap>>> codecs = config.codecs

        then:
        codecs.size() == 2

        when:
        List<Codec<TextMap>> headerCodecs = codecs[HTTP_HEADERS]

        then:
        headerCodecs.size() == 2
        headerCodecs[0] instanceof TraceContextCodec // W3C
        headerCodecs[1] instanceof B3TextMapCodec // B3

        when:
        List<Codec<TextMap>> textMapCodecs = codecs[TEXT_MAP]

        then:
        textMapCodecs.size() == 2
        textMapCodecs[0] instanceof TraceContextCodec // W3C
        textMapCodecs[1] instanceof B3TextMapCodec // B3
    }

    void 'test codec configuration W3C B3 JAEGER'() {
        given:
        run('tracing.jaeger.codecs': 'W3C,B3,JAEGER')

        when:
        CodecConfiguration config = ctx.getBean(JaegerConfiguration).configuration.codec
        Map<Format<?>, List<Codec<TextMap>>> codecs = config.codecs

        then:
        codecs.size() == 2

        when:
        List<Codec<TextMap>> headerCodecs = codecs[HTTP_HEADERS]

        then:
        headerCodecs.size() == 3
        headerCodecs[0] instanceof TraceContextCodec // W3C
        headerCodecs[1] instanceof B3TextMapCodec // B3
        headerCodecs[2] instanceof TextMapCodec // JAEGER

        when:
        Map<Format<?>, List<Codec<Binary>>> binaryCodecs = config.binaryCodecs

        then:
        binaryCodecs.size() == 1

        when:
        List<Codec<Binary>> jaegerCodecs = binaryCodecs[BINARY]

        then:
        jaegerCodecs.size() == 1
        jaegerCodecs[0] instanceof BinaryCodec // JAEGER

        when:
        List<Codec<TextMap>> textMapCodecs = codecs[TEXT_MAP]

        then:
        textMapCodecs.size() == 3
        textMapCodecs[0] instanceof TraceContextCodec // W3C
        textMapCodecs[1] instanceof B3TextMapCodec // B3
        textMapCodecs[2] instanceof TextMapCodec // JAEGER
    }

    private void run(Map<String, Object> properties) {
        ctx = ApplicationContext.run(['tracing.jaeger.enabled': 'true'] + properties)
    }
}
