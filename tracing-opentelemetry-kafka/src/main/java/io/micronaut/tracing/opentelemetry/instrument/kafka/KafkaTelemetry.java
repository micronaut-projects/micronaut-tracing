package io.micronaut.tracing.opentelemetry.instrument.kafka;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.function.BiFunction;

import io.micronaut.core.util.CollectionUtils;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.kafka.internal.KafkaHeadersSetter;
import io.opentelemetry.instrumentation.kafka.internal.KafkaProcessRequest;
import io.opentelemetry.instrumentation.kafka.internal.KafkaProducerRequest;
import io.opentelemetry.instrumentation.kafka.internal.KafkaUtil;
import io.opentelemetry.instrumentation.kafka.internal.OpenTelemetryMetricsReporter;
import io.opentelemetry.instrumentation.kafka.internal.OpenTelemetrySupplier;

import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.metrics.MetricsReporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class KafkaTelemetry {

    private static final Logger LOG = LoggerFactory.getLogger(KafkaTelemetry.class);

    private static final String METHOD_SEND = "send";
    private static final String METHOD_POLL = "poll";

    private static final TextMapSetter<Headers> SETTER = KafkaHeadersSetter.INSTANCE;
    private static final Future<RecordMetadata> EMPTY_FUTURE = CompletableFuture.completedFuture(null);

    private final OpenTelemetry openTelemetry;
    private final Instrumenter<KafkaProducerRequest, RecordMetadata> producerInstrumenter;
    private final Instrumenter<KafkaProcessRequest, Void> consumerProcessInstrumenter;
    @SuppressWarnings("rawtypes")
    private final Collection<KafkaTelemetryProducerTracingFilter> producerTracingFilters;
    @SuppressWarnings("rawtypes")
    private final Collection<KafkaTelemetryConsumerTracingFilter> consumerTracingFilters;
    private final KafkaTelemetryProperties kafkaTelemetryProperties;
    private final boolean producerPropagationEnabled;

    @SuppressWarnings("rawtypes")
    public KafkaTelemetry(OpenTelemetry openTelemetry, Instrumenter<KafkaProducerRequest, RecordMetadata> producerInstrumenter,
                          Instrumenter<KafkaProcessRequest, Void> consumerProcessInstrumenter,
                          Collection<KafkaTelemetryProducerTracingFilter> producerTracingFilters,
                          Collection<KafkaTelemetryConsumerTracingFilter> consumerTracingFilters,
                          KafkaTelemetryProperties kafkaTelemetryProperties, boolean producerPropagationEnabled) {
        this.openTelemetry = openTelemetry;
        this.producerInstrumenter = producerInstrumenter;
        this.consumerProcessInstrumenter = consumerProcessInstrumenter;
        this.producerTracingFilters = producerTracingFilters;
        this.consumerTracingFilters = consumerTracingFilters;
        this.kafkaTelemetryProperties = kafkaTelemetryProperties;
        this.producerPropagationEnabled = producerPropagationEnabled;
    }

    /**
     * Returns a new KafkaTelemetry configured with the given {@link OpenTelemetry}.
     *
     * @param openTelemetry openTelemetry instance
     * @param kafkaTelemetryProperties kafkaTelemetryProperties instance
     * @param consumerTracingFilters list of consumerTracingFilters
     * @param producerTracingFilters list of producerTracingFilters
     *
     * @return kafkaTelemetry instance
     */
    @SuppressWarnings("rawtypes")
    public static KafkaTelemetry create(OpenTelemetry openTelemetry, KafkaTelemetryProperties kafkaTelemetryProperties,
                                        Collection<KafkaTelemetryConsumerTracingFilter> consumerTracingFilters,
                                        Collection<KafkaTelemetryProducerTracingFilter> producerTracingFilters) {
        return builder(openTelemetry, kafkaTelemetryProperties, consumerTracingFilters, producerTracingFilters).build();
    }

    /**
     * Returns a new {@link KafkaTelemetryBuilder} configured with the given {@link OpenTelemetry}.
     *
     * @param openTelemetry openTelemetry instance
     * @param kafkaTelemetryProperties kafkaTelemetryProperties instance
     * @param consumerTracingFilters list of consumerTracingFilters
     * @param producerTracingFilters list of producerTracingFilters
     *
     * @return KafkaTelemetryBuilder object
     */
    @SuppressWarnings("rawtypes")
    public static KafkaTelemetryBuilder builder(OpenTelemetry openTelemetry, KafkaTelemetryProperties kafkaTelemetryProperties,
                                                Collection<KafkaTelemetryConsumerTracingFilter> consumerTracingFilters,
                                                Collection<KafkaTelemetryProducerTracingFilter> producerTracingFilters) {
        return new KafkaTelemetryBuilder(openTelemetry, kafkaTelemetryProperties, consumerTracingFilters, producerTracingFilters);
    }

    private TextMapPropagator propagator() {
        return openTelemetry.getPropagators().getTextMapPropagator();
    }

    /**
     * Produces a set of kafka client config properties (consumer or producer) to register a {@link
     * MetricsReporter} that records metrics to an {@code openTelemetry} instance. Add these resulting
     * properties to the configuration map used to initialize a {@link KafkaConsumer} or {@link
     * KafkaProducer}.
     *
     * <p>For producers:
     *
     * <pre>{@code
     * //    Map<String, Object> config = new HashMap<>();
     * //    config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, ...);
     * //    config.putAll(kafkaTelemetry.metricConfigProperties());
     * //    try (KafkaProducer<?, ?> producer = new KafkaProducer<>(config)) { ... }
     * }</pre>
     *
     * <p>For consumers:
     *
     * <pre>{@code
     * //    Map<String, Object> config = new HashMap<>();
     * //    config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, ...);
     * //    config.putAll(kafkaTelemetry.metricConfigProperties());
     * //    try (KafkaConsumer<?, ?> consumer = new KafkaConsumer<>(config)) { ... }
     * }</pre>
     *
     * @return the kafka client properties
     */
    public Map<String, ?> metricConfigProperties() {
        Map<String, Object> config = new HashMap<>();
        config.put(CommonClientConfigs.METRIC_REPORTER_CLASSES_CONFIG, OpenTelemetryMetricsReporter.class.getName());
        config.put(OpenTelemetryMetricsReporter.CONFIG_KEY_OPENTELEMETRY_SUPPLIER, new OpenTelemetrySupplier(openTelemetry));
        config.put(OpenTelemetryMetricsReporter.CONFIG_KEY_OPENTELEMETRY_INSTRUMENTATION_NAME, "io.opentelemetry.kafka-clients-2.6");
        return config;
    }

    /**
     * Build and inject span into record.
     *
     * @param record the producer record to inject span info.
     * @param <K> key class
     * @param <V> value class
     */
    public <K, V> void buildAndInjectSpan(ProducerRecord<K, V> record, String clientId) {
        Context parentContext = Context.current();
        KafkaProducerRequest request = KafkaProducerRequest.create(record, clientId);

        if (!producerInstrumenter.shouldStart(parentContext, request)) {
            return;
        }

        Context context = producerInstrumenter.start(parentContext, request);
        if (producerPropagationEnabled) {
            try {
                propagator().inject(context, record.headers(), SETTER);
            } catch (Throwable t) {
                // it can happen if headers are read only (when record is sent second time)
                LOG.warn("Failed to inject span context. sending record second time?", t);
            }
        }
        producerInstrumenter.end(context, request, null, null);
    }

    /**
     * Build and inject span into record.
     *
     * @param record the producer record to inject span info.
     * @param callback the producer send callback
     * @param <K> key class
     * @param <V> value class
     * @param sendFn send function
     *
     * @return send function's result
     */
    public <K, V> Future<RecordMetadata> buildAndInjectSpan(ProducerRecord<K, V> record, Producer<K, V> producer, Callback callback,
                                                            BiFunction<ProducerRecord<K, V>, Callback, Future<RecordMetadata>> sendFn) {
        Context parentContext = Context.current();
        KafkaProducerRequest request = KafkaProducerRequest.create(record, producer);
        if (!producerInstrumenter.shouldStart(parentContext, request)) {
            return sendFn.apply(record, callback);
        }

        Context context = producerInstrumenter.start(parentContext, request);
        try (Scope ignored = context.makeCurrent()) {
            propagator().inject(context, record.headers(), SETTER);
            callback = new ProducerCallback(callback, parentContext, context, request);
            return sendFn.apply(record, callback);
        }
    }

    public <K, V> Future<RecordMetadata> buildAndFinishSpan(ProducerRecord<K, V> record, Producer<K, V> producer, Callback callback,
                                                            BiFunction<ProducerRecord<K, V>, Callback, Future<RecordMetadata>> sendFn) {
        Context currentContext = Context.current();
        KafkaProducerRequest request = KafkaProducerRequest.create(record, producer);

        if (!producerInstrumenter.shouldStart(currentContext, request)) {
            return sendFn == null ? EMPTY_FUTURE : sendFn.apply(record, callback);
        }

        Context context = producerInstrumenter.start(currentContext, request);
        if (producerPropagationEnabled) {
            try {
                propagator().inject(context, record.headers(), SETTER);
            } catch (Throwable t) {
                // it can happen if headers are read only (when record is sent second time)
                LOG.warn("failed to inject span context. sending record second time?", t);
            }
        }
        producerInstrumenter.end(context, request, null, null);
        if (sendFn == null) {
            return EMPTY_FUTURE;
        }

        callback = new ProducerCallback(callback, currentContext, context, request);
        return sendFn.apply(record, callback);
    }

    public <K, V> void buildAndFinishSpan(ProducerRecord<K, V> record, String clientId) {
        Context currentContext = Context.current();
        KafkaProducerRequest request = KafkaProducerRequest.create(record, clientId);
        if (!producerInstrumenter.shouldStart(currentContext, request)) {
            return;
        }

        Context context = producerInstrumenter.start(currentContext, request);
        if (producerPropagationEnabled) {
            try {
                propagator().inject(context, record.headers(), SETTER);
            } catch (Throwable t) {
                // it can happen if headers are read only (when record is sent second time)
                LOG.warn("failed to inject span context. sending record second time?", t);
            }
        }
        producerInstrumenter.end(context, request, null, null);
    }

    private <K, V> void buildAndFinishSpan(ConsumerRecords<K, V> records, Consumer<K, V> consumer) {
        buildAndFinishSpan(records, KafkaUtil.getConsumerGroup(consumer), KafkaUtil.getClientId(consumer));
    }

    public <K, V> void buildAndFinishSpan(ConsumerRecords<K, V> records, String consumerGroup, String clientId) {
        Context currentContext = Context.current();
        for (ConsumerRecord<K, V> record : records) {
            processConsumerRecord(currentContext, record, consumerGroup, clientId);
        }
    }

    private <K, V> void buildAndFinishSpan(List<ConsumerRecord<K, V>> records, Consumer<K, V> consumer) {
        buildAndFinishSpan(records, KafkaUtil.getConsumerGroup(consumer), KafkaUtil.getClientId(consumer));
    }

    public <K, V> void buildAndFinishSpan(List<ConsumerRecord<K, V>> records, String consumerGroup, String clientId) {
        Context currentContext = Context.current();
        for (ConsumerRecord<K, V> record : records) {
            processConsumerRecord(currentContext, record, consumerGroup, clientId);
        }
    }

    private <K, V> void processConsumerRecord(Context parentContext, ConsumerRecord<K, V> record, String consumerGroup, String clientId) {
        KafkaProcessRequest request = KafkaProcessRequest.create(record, consumerGroup, clientId);
        if (!consumerProcessInstrumenter.shouldStart(parentContext, request)) {
            return;
        }
        Context current = consumerProcessInstrumenter.start(parentContext, request);
        consumerProcessInstrumenter.end(current, request, null, null);
    }

    /**
     * Returns `true` if current topic need to exclude for tracing.
     *
     * @param topic the topic
     *
     * @return nedd or not exclude topic for tracing.
     */
    public boolean excludeTopic(String topic) {
        if (CollectionUtils.isNotEmpty(kafkaTelemetryProperties.getIncludedTopics())) {
            for (String includedTopic : kafkaTelemetryProperties.getIncludedTopics()) {
                if (includedTopic.equalsIgnoreCase(topic)) {
                    return false;
                }
            }
            return true;
        }
        if (CollectionUtils.isNotEmpty(kafkaTelemetryProperties.getExcludedTopics())) {
            for (String excludedTopic : kafkaTelemetryProperties.getExcludedTopics()) {
                if (excludedTopic.equalsIgnoreCase(topic)) {
                    return true;
                }
            }
        }
        return false;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public <K, V> boolean filterConsumerRecord(ConsumerRecord<K, V> record, Consumer<K, V> consumer) {
        if (CollectionUtils.isEmpty(consumerTracingFilters)) {
            return true;
        }
        for (KafkaTelemetryConsumerTracingFilter filter : consumerTracingFilters) {
            if (!filter.filter(record, consumer)) {
                return false;
            }
        }
        return true;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public <K, V> boolean filterProducerRecord(ProducerRecord<K, V> record, Producer<K, V> producer) {
        if (CollectionUtils.isEmpty(producerTracingFilters)) {
            return true;
        }
        for (KafkaTelemetryProducerTracingFilter filter : producerTracingFilters) {
            if (!filter.filter(record, producer)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns a decorated {@link Producer} that consumes spans for each produced message.
     *
     * @param <K> key class
     * @param <V> value class
     * @param producer kafka consumer
     *
     * @return proxy object with tracing logic for producer
     */
    @SuppressWarnings("unchecked")
    public <K, V> Producer<K, V> wrap(Producer<K, V> producer) {
        return (Producer<K, V>) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {Producer.class},
            (proxy, method, args) -> {
                // Future<RecordMetadata> send(ProducerRecord<K, V> record)
                // Future<RecordMetadata> send(ProducerRecord<K, V> record, Callback callback)
                if (!METHOD_SEND.equals(method.getName()) || method.getParameterCount() == 0 || method.getParameterTypes()[0] != ProducerRecord.class) {
                    return method.invoke(producer, args);
                }

                ProducerRecord<K, V> record = (ProducerRecord<K, V>) args[0];

                if (excludeTopic(record.topic()) || !filterProducerRecord(record, producer)) {
                    return method.invoke(producer, args);
                }

                Callback callback = null;
                if (method.getParameterCount() >= 2 && method.getParameterTypes()[1] == Callback.class) {
                    callback = (Callback) args[1];
                }
                return buildAndFinishSpan(record, producer, callback, producer::send);
            });
    }

    /**
     * Returns a decorated {@link Consumer} that consumes spans for each received message.
     *
     * @param <K> key class
     * @param <V> value class
     * @param consumer kafka consumer
     *
     * @return proxy object with tracing logic for consumer
     */
    @SuppressWarnings("unchecked")
    public <K, V> Consumer<K, V> wrap(Consumer<K, V> consumer) {
        return (Consumer<K, V>) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {Consumer.class},
            (proxy, method, args) -> {
                Object result = method.invoke(consumer, args);
                if (!METHOD_POLL.equals(method.getName()) || method.getReturnType() != ConsumerRecords.class) {
                    return result;
                }

                List<ConsumerRecord<K, V>> recordsToTrace = new ArrayList<>();
                ConsumerRecords<K, V> records = (ConsumerRecords<K, V>) result;
                for (ConsumerRecord<K, V> record : records) {
                    if (excludeTopic(record.topic()) || !filterConsumerRecord(record, consumer)) {
                        continue;
                    }
                    recordsToTrace.add(record);
                }

                buildAndFinishSpan(recordsToTrace, consumer);
                return result;
            });
    }

    public KafkaTelemetryProperties getKafkaTelemetryProperties() {
        return kafkaTelemetryProperties;
    }

    private class ProducerCallback implements Callback {

        private final Callback callback;
        private final Context parentContext;
        private final Context context;
        private final KafkaProducerRequest request;

        private ProducerCallback(Callback callback, Context parentContext, Context context, KafkaProducerRequest request) {
            this.callback = callback;
            this.parentContext = parentContext;
            this.context = context;
            this.request = request;
        }

        @Override
        public void onCompletion(RecordMetadata metadata, Exception exception) {
            producerInstrumenter.end(context, request, metadata, exception);

            if (callback != null) {
                try (Scope ignored = parentContext.makeCurrent()) {
                    callback.onCompletion(metadata, exception);
                }
            }
        }
    }
}
