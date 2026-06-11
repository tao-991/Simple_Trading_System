package com.trading.kafka;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

import java.util.Properties;

/**
 * Central Kafka configuration.
 *
 * All topic names and connection settings live here.
 * Never scatter magic strings like "executions" across the codebase.
 * **/


public class KafkaConfig {
    /**
     * This class has one job: define all topic names and all Kafka settings in one place. In a real bank
     * this would be loaded from a config file, but constants are fine for now
     * **/

    // -- Broker address --
    // This matches the port in your Docker Compse file
    public static final String BOOTSTRAP_SERVERS = "localhost:9092";

    // -- Topic names --
    public static final String TOPIC_ORDERS = "orders";
    public static final String TOPIC_EXECUTIONS = "executions";
    public static final String TOPIC_MKTDATA = "mktdata.tick";
    public static final String TOPIC_POSITIONS = "positions";
    public static final String TOPIC_RISK_ALERTS = "risk.alerts";

    // -- Producer config --
    public static Properties producerProps(){
        Properties props = new Properties();

        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);

        // Key and value are both plain strings (we serialize to JSON ourselves)
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

        // acks=all: wait for ALL in-sync replicase to confirm before returning.
        // This is the strongest durability guarantee. Slightly slower but
        // in a trading system you never want to lose a trade event.
        props.put(ProducerConfig.ACKS_CONFIG, "all");

        /**
         * Idempotent producer: Kafka assigns each message a sequence number.
         * If the broker receives the same message twice (due to a retry), it
         * deduplicates automatically. Prevent phantom trades.
         * **/
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);

        // How long to wait before giving up on a send (10 seconds)
        props.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 10_000);

        return props;

    }

    // -- Consumer Config --
    public static Properties consumerProps(String groupId) {
        Properties props = new Properties();

        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);

        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());

        // Disable auto-commit. We commit mannually AFTER processing succeeds.
        // This gives us at-least-once delivery: if we crash mid-process,
        // the offset is not committed, so we re-read and re-process on restart.
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

        //If no committed offset exists for this group (first run), start
        // from the earliest available message, not the latest.
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        return props;
    }
}
