package com.trading.matching;

import com.trading.common.OrderSide;
import com.trading.common.OrderType;
import com.trading.common.TimeInForce;
import com.trading.model.Order;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

public class EngineDemo {

    private static final AtomicInteger orderCounter = new AtomicInteger(0);

    public static void main(String[] args) throws InterruptedException {

        // tradeHandler: called on the symbol's matching thread after each match
        MatchingEngine engine = new MatchingEngine(trades -> {
            trades.forEach(t ->
                    System.out.printf("[TradeHandler] %s%n", t));
        });

        engine.registerSymbol("AAPL");
        engine.registerSymbol("TSLA");

        // ── Scenario: AAPL and TSLA orders submitted concurrently ──
        // CountDownLatch lets us wait until all matching is done before printing results
        CountDownLatch latch = new CountDownLatch(1);

        // AAPL: rest a sell, then send a matching buy
        engine.submitOrder(limit("AAPL", OrderSide.SELL, 150.15, 300));
        engine.submitOrder(limit("AAPL", OrderSide.SELL, 150.20, 500));

        // TSLA: concurrent, different thread - won't interfere with AAPL
        engine.submitOrder(limit("TSLA", OrderSide.SELL, 800.00, 100));
        engine.submitOrder(limit("TSLA", OrderSide.BUY,  800.00, 100));  // should match

        // AAPL: incoming buy crosses two levels
        engine.submitOrder(limit("AAPL", OrderSide.BUY,  150.20, 400));

        // Give matching threads time to process before we read results
        Thread.sleep(200);

        System.out.println("\n── Final Book State ──");
        System.out.printf("AAPL Best Bid: %.2f  Best Ask: %.2f%n",
                engine.getBestBid("AAPL"), engine.getBestAsk("AAPL"));
        System.out.printf("TSLA Best Bid: %.2f  Best Ask: %.2f%n",
                engine.getBestBid("TSLA"), engine.getBestAsk("TSLA"));

        engine.shutdown();
    }

    private static Order limit(String symbol, OrderSide side, double price, long qty) {
        String id = symbol + "-" + orderCounter.incrementAndGet();
        return new Order(id, "CL-" + id, "ACC001", symbol,
                side, OrderType.LIMIT, qty, price, TimeInForce.GTC);
    }
}