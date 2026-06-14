package com.trading.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trading.matching.Trade;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Publishes Trade events to the Kafka 'executions' topic.
 *
 * Called by the MatchingEngine's tradeHandler after each matching cycle.
 *
 * Message format:
 *  key: orderId of the incoming order ( routes all events for one order to the same partition -> ordered)
 *  Value: JSON serialization of the Trade object.
 *
 * Example message:
 *  Key: "AAPL-7"
 *  Value: {"tradeId":"T-3", "incomingOrderId":"AAPL-7",
 *          "restingOrderId":"AAPL-2","symbol":"AAPL",
 *          "matchedQty":300, "tradePrice":150.15}
 * **/

public class TradeEventProducer implements AutoCloseable{

    private static final Logger log = LoggerFactory.getLogger(TradeEventProducer.class);

    private final KafkaProducer<String, String> producer;
    private final ObjectMapper json = new ObjectMapper();

    public TradeEventProducer() {
        this.producer = new KafkaProducer<>(KafkaConfig.producerProps());
    }

    /**
     * Publish all trades from one matching cycle.
     * One Kafka message per Trade object.
     * */
    public void publishTrades(List<Trade> trades){
        for (Trade trade : trades){
            publish(trade);
        }
    }

    public void publish(Trade trade){
        try{
            String key = trade.getIncomingOrderId(); // routes to stable partition
            String value = json.writeValueAsString(trade);

            ProducerRecord<String, String> record =
                    new ProducerRecord<>(KafkaConfig.TOPIC_EXECUTIONS, key, value);

            // send() is non-blocking - returns a Future.
            // The callback fires on a background thread when the broker responds.
            producer.send(record, (metadata, exception) -> {
               if (exception != null) {
                   // In production: dead-letter queue, alert, circuit breaker.
                   log.error("[TradeEventProducer] Failed to publish trade {}:{}",
                           trade.getTradeId(), exception.getMessage());
               } else {
                   log.debug("[TradeEventProducer] Published trade {} -> topic={} partition={} offset={}",
                           trade.getTradeId(),
                           metadata.topic(),
                           metadata.partition(),
                           metadata.offset());
               }
            });
        }catch (Exception e){
            log.error("[TradeEventProducer] Serialization error for trade {}:{}",
                    trade.getTradeId(),
                    e.getMessage());
        }
    }

    @Override
    public void close() {
        // flush() blocks until all buffered messages are sent.
        // Always call this before shutdown - otherwise buffered trades are lost.
        producer.flush();
        producer.close();
        log.info("[TradeEventProducer] Closed.");
    }
}
