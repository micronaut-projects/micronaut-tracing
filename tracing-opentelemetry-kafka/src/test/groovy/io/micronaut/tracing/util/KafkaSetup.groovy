package io.micronaut.tracing.util

import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.clients.admin.NewTopic
import org.testcontainers.kafka.KafkaContainer
import org.testcontainers.utility.DockerImageName
import groovy.transform.CompileStatic

@CompileStatic
class KafkaSetup {
    // Using a singleton container instance
    private static KafkaContainer kafkaContainer
    public static final String MY_STREAM = "my-stream"

    /**
     * Returns the configuration map required for Micronaut to connect to the test container.
     * Automatically starts the container and creates topics if not already initialized.
     */
    static Map<String, String> getProperties() {
        if (kafkaContainer == null) {
            kafkaContainer = new KafkaContainer(DockerImageName.parse("apache/kafka:latest"))
            kafkaContainer.start()
            createTopics(["my-stream"])
        }

        return [
                "kafka.bootstrap.servers": kafkaContainer.getBootstrapServers(),
                "kafka.enabled"          : "true"
        ]
    }

    private static void createTopics(List<String> topicNames) {
        Map<String, Object> config = [
                (AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG): kafkaContainer.getBootstrapServers()
        ] as Map<String, Object>

        // Use .withCloseable to ensure the AdminClient is shut down immediately
        AdminClient.create(config).withCloseable { admin ->
            def newTopics = topicNames.collect { name -> new NewTopic(name, 1, (short) 1) }
            admin.createTopics(newTopics).all().get() // .get() ensures topics are created before proceeding
        }
    }
}
