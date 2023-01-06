package io.micronaut.tracing.util

import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.NewTopic
import org.testcontainers.containers.KafkaContainer

class KafkaSetup {
    static KafkaContainer kafkaContainer

    static String MY_STREAM = "my-stream"

    static KafkaContainer init() {
        if (kafkaContainer == null) {
            kafkaContainer = new KafkaContainer("5.4.2")
            kafkaContainer.start()
            createTopics()
        }
        return kafkaContainer
    }

    static void destroy() {
        if (kafkaContainer) {
            kafkaContainer.stop()
            kafkaContainer = null
        }
    }

    //Override to create different topics on startup
    private static List<String> getTopics() {
        return [MY_STREAM]
    }

    private static void createTopics() {
        def newTopics = topics.collect { topic -> new NewTopic(topic, 1, (short) 1) }
        def admin = AdminClient.create(["bootstrap.servers": kafkaContainer.getBootstrapServers()])
        admin.createTopics(newTopics)
    }
}
