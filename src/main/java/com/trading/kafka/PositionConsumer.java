package com.trading.kafka;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trading.matching.Trade;
import com.trading.common.OrderSide;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import com.trading.redis.PositionStore;


/**
 * Consumes Trade events from the 'executions' topic and maintains
 * a simple net position per symbol.
 *
 * This is a STANDALONE program. It does not know about MatchingEngine or
 * OrderManager - it only knows the Kafka topic. That is the whole point of
 * the message bus: full decoupling.
 * */

public class PositionConsumer {

    private static final Logger log = LoggerFactory.getLogger(PositionConsumer.class);

    private final KafkaConsumer<String, String> consumer;
    private final ObjectMapper json = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    // symbol -> net signed quantity (very simplified position view)
    private final Map<String, Long> positions = new ConcurrentHashMap<>();

    // Idempotency guard: tradeIds we have already applied.
    // Without this, an at-least-once redeliver would double-count the position.
    private final Set<String> processedTradeIds = ConcurrentHashMap.newKeySet();

    private volatile boolean running = true;

    private final PositionStore positionStore;

    public PositionConsumer(){
        // "position-manager-group" is this consumer's group id
        this.consumer = new KafkaConsumer<>(
                KafkaConfig.consumerProps("position-manager-group")
        );
        this.positionStore = new PositionStore("localhost",6379);
    }

    public void run(){
        // Subscribe to the topic - Kafka assigns us its partitions
        consumer.subscribe(List.of(KafkaConfig.TOPIC_EXECUTIONS));
        log.info("[PositionConsumer] Subscribed to {}", KafkaConfig.TOPIC_EXECUTIONS);

        try{
            // -- The poll loop --
            while(running){
                ConsumerRecords<String, String> records =
                        consumer.poll(Duration.ofMillis(100));

                for (ConsumerRecord<String, String> record : records) {
                    processRecord(record);
                }

                // Commit AFTER processing the whole batch -> at-least-once
                if (!records.isEmpty()){
                    consumer.commitSync();
                }
            }
        } catch (Exception e){
            log.error("[PositionConsumer] Error in poll loop: {}", e.getMessage(),e);
        } finally {
            consumer.close();
            log.info("[PositionConsumer] closed.");
        }
    }

    private void processRecord(ConsumerRecord<String, String> record) {
        try{
            // Deserialize JSON back into a Trade object (round-trip)
            Trade trade = json.readValue(record.value(), Trade.class);

            // --- Idempotency check ---
            // If we've seen this tradeId before, skip silently.
            // This make at-least-once delivery safe: the same Trade can be
            // Delivered N times, but the position is only updated once
            if (!processedTradeIds.add(trade.getTradeId())){
                log.debug("[PositionConsumer] Skipping duplicate trade {}", trade.getTradeId());
                return;
            }

            // Update position for the symbol.
            // Simplification: the incoming order's side decides direction,
            // but a Trade has two sides. For now we just track gross matched
            // quantity per symbol as a placeholder for real position logic.
//            positions.merge(trade.getSymbol(), trade.getMatchedQty(), Long::sum);

            applyToPosition(trade.getIncomingAccountId(), trade.getIncomingSide(), trade);
            applyToPosition(trade.getRestingAccountId(), trade.getRestingSide(), trade);

            log.debug("[PositionConsumer] Applied trade {} | {} qty={} @ {} | partition={} offset={}",
                    trade.getTradeId(),
                    trade.getSymbol(),
                    trade.getMatchedQty(),
                    trade.getTradePrice(),
                    record.partition(),
                    record.offset());

//            log.info("[PositionConsumer] Position {} = {}",
//                    trade.getSymbol(), positions.get(trade.getSymbol()));
        }catch (Exception e){
            // A poison message (bad JSON) should not kill the consumer.
            log.error("[PositionConsumer] Failed to process record at offset {}:{}",
                    record.offset(), e.getMessage());
        }
    }

    private void applyToPosition(String accountId, OrderSide side, Trade trade){
        // Read current state from Redis
        long oldNetQty = positionStore.getNetQty(accountId, trade.getSymbol());
        double oldAvgCost = positionStore.getAvgCost(accountId, trade.getSymbol());
        double realizedPnL = positionStore.getRealizedPnL(accountId, trade.getSymbol());

        long newNetQty;
        double newAvgCost;

        if (side == OrderSide.BUY){
            newNetQty = oldNetQty + trade.getMatchedQty();
            // Weighted average cost: blend old position cost with new fill cost
            newAvgCost = (oldAvgCost == 0) ? trade.getTradePrice() : (oldNetQty * oldAvgCost + trade.getMatchedQty() * trade.getTradePrice()) / (oldNetQty + trade.getMatchedQty());
            // realizedPnL unchange on a buy
        } else {
            newNetQty = oldNetQty - trade.getMatchedQty();
            newAvgCost = oldAvgCost; // cost basis unchanged when selling
            realizedPnL += (trade.getTradePrice() - oldAvgCost) * trade.getMatchedQty();
        }

        positionStore.updatePosition(accountId, trade.getSymbol(), newNetQty, newAvgCost, realizedPnL);

        log.info("[PositionConsumer] {} {} | netQty={} avgCost={} realizedPnL={}",
                accountId, trade.getSymbol(), newNetQty, newAvgCost, realizedPnL);

    }

    public void stop(){
        this.running = false;
    }

    public static void main(String[] args) {
        PositionConsumer consumer = new PositionConsumer();

        // Allow Ctrl+C to stop the loop gracefully
        Runtime.getRuntime().addShutdownHook(new Thread(consumer::stop));

        consumer.run();
    }

}
