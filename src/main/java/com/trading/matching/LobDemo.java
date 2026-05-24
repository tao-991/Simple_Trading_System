package com.trading.matching;

import com.trading.common.OrderSide;
import com.trading.common.OrderType;
import com.trading.common.TimeInForce;
import com.trading.model.Order;

public class LobDemo {

    public static void main(String[] args) {
        LimitOrderBook book = new LimitOrderBook("AAPL");

        // --- Add some bid orders ---
        book.addOrder(limit("B1", OrderSide.BUY,  150.10, 500));
        book.addOrder(limit("B2", OrderSide.BUY,  150.05, 800));
        book.addOrder(limit("B3", OrderSide.BUY,  150.10, 200));  // same price as B1, sits behind
        book.addOrder(limit("B4", OrderSide.BUY,  150.00, 1000));

        // --- Add some ask orders ---
        book.addOrder(limit("S1", OrderSide.SELL, 150.15, 300));
        book.addOrder(limit("S2", OrderSide.SELL, 150.20, 600));
        book.addOrder(limit("S3", OrderSide.SELL, 150.15, 100));  // same price as S1, sits behind

        System.out.println(book);
        System.out.printf("Best Bid:  %.2f%n", book.getBestBid());
        System.out.printf("Best Ask:  %.2f%n", book.getBestAsk());
        System.out.printf("Spread:    %.2f%n", book.getSpread());
        System.out.printf("Mid:       %.3f%n", book.getMidPrice());

        // --- Cancel one order: 150.10 bid level should still exist (B3 remains) ---
        System.out.println("\n--- Cancel B1 (500 qty removed from 150.10 level) ---");
        Order canceled = book.cancelOrder("B1");
        System.out.println("Canceled: " + canceled);
        System.out.println(book);

        // --- Cancel B3: 150.10 level should be pruned entirely ---
        System.out.println("\n--- Cancel B3 (150.10 level should disappear) ---");
        book.cancelOrder("B3");
        System.out.println(book);
        System.out.printf("New Best Bid: %.2f  (expected 150.05)%n", book.getBestBid());

        // --- Cancel a non-existent order ---
        System.out.println("\n--- Cancel nonexistent order ---");
        Order shouldBeNull = book.cancelOrder("XXX");
        System.out.println("Result: " + shouldBeNull);
    }

    /** Helper: build a limit order for AAPL with the given parameters. */
    private static Order limit(String id, OrderSide side, double price, long qty) {
        return new Order(
                id,                  // orderId
                "CL-" + id,          // clOrdId
                "ACC001",            // accountId
                "AAPL",              // symbol
                side,
                OrderType.LIMIT,
                qty,
                price,
                TimeInForce.GTC      // <-- replace with a value that exists in your enum
        );
    }
}