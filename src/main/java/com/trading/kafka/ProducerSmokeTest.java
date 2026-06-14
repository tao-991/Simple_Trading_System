package com.trading.kafka;

import com.trading.matching.Trade;

import java.util.List;

/**
 * Smoke test: verify we can publish a single Trade to Kafka.
 * Run this BEFORE wiring Kafka into the matching engine, so we can
 * isolate "can we reach the broker?" from "does matching work?".
 */
public class ProducerSmokeTest {

    public static void main(String[] args) throws InterruptedException {

        // try-with-resources: producer.close() (which flushes) runs
        // automatically when the block exits, even on exception.
        try (TradeEventProducer producer = new TradeEventProducer()) {

            // Build one fake trade by hand — no matching engine involved
            Trade fakeTrade = new Trade(
                    "AAPL-7",    // incomingOrderId → becomes the Kafka key
                    "AAPL-2",    // restingOrderId
                    "AAPL",      // symbol
                    300,         // matchedQty
                    150.15       // tradePrice
            );

            System.out.println("Publishing one fake trade to Kafka...");
            producer.publishTrades(List.of(fakeTrade));

            // Give the async send + callback a moment before flush()/close()
            Thread.sleep(1000);

            System.out.println("Done. Check the log above for partition + offset.");
        }
    }
}