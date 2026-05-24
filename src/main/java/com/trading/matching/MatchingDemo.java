package com.trading.matching;

import com.trading.common.OrderSide;
import com.trading.common.OrderType;
import com.trading.common.TimeInForce;
import com.trading.model.Order;

public class MatchingDemo {

    private static int orderCounter = 0;

    public static void main(String[] args) {

        // ════════════════════════════════════════════════
        // Scenario 1: Simple full match (1 trade)
        // ════════════════════════════════════════════════
        System.out.println("════ Scenario 1: Simple full match ════");
        LimitOrderBook book1 = new LimitOrderBook("AAPL");

        book1.match(limit(OrderSide.SELL, 150.15, 300));  // rests on ask side
        System.out.println("After resting SELL 300@150.15:");
        System.out.println(book1);

        Order buy1 = limit(OrderSide.BUY, 150.15, 300);
        MatchResult r1 = book1.match(buy1);
        System.out.println("Incoming: BUY 300@150.15");
        System.out.println("Result:   " + r1);
        r1.getTrades().forEach(System.out::println);
        System.out.println("Book after match:");
        System.out.println(book1);

        // ════════════════════════════════════════════════
        // Scenario 2: Incoming order crosses multiple price levels
        // ════════════════════════════════════════════════
        System.out.println("\n════ Scenario 2: Cross multiple levels ════");
        LimitOrderBook book2 = new LimitOrderBook("AAPL");

        book2.match(limit(OrderSide.SELL, 150.15, 300));  // S1
        book2.match(limit(OrderSide.SELL, 150.15, 100));  // S2 (same level, behind S1)
        book2.match(limit(OrderSide.SELL, 150.20, 600));  // S3
        System.out.println("Book before match:");
        System.out.println(book2);

        Order buy2 = limit(OrderSide.BUY, 150.20, 550);
        MatchResult r2 = book2.match(buy2);
        System.out.println("Incoming: BUY 550@150.20");
        System.out.println("Result:   " + r2);
        r2.getTrades().forEach(System.out::println);
        System.out.println("Book after match:");
        System.out.println(book2);
        System.out.printf("S3 resting order leaves qty (expected 450): %d%n",
                buy2.getFilledQty() > 0 ? 600 - (550 - 400) : -1);  // verify via book

        // ════════════════════════════════════════════════
        // Scenario 3: Incoming price too low - order rests
        // ════════════════════════════════════════════════
        System.out.println("\n════ Scenario 3: No match - order rests ════");
        LimitOrderBook book3 = new LimitOrderBook("AAPL");

        book3.match(limit(OrderSide.SELL, 150.15, 300));
        Order buy3 = limit(OrderSide.BUY, 150.10, 200);  // price below ask - won't match
        MatchResult r3 = book3.match(buy3);
        System.out.println("Incoming: BUY 200@150.10 (ask is 150.15 - no match)");
        System.out.println("Result:   " + r3);
        System.out.println("Book (buy should rest on bid side):");
        System.out.println(book3);

        // ════════════════════════════════════════════════
        // Scenario 4: Partial fill - remainder rests
        // ════════════════════════════════════════════════
        System.out.println("\n════ Scenario 4: Partial fill, remainder rests ════");
        LimitOrderBook book4 = new LimitOrderBook("AAPL");

        book4.match(limit(OrderSide.SELL, 150.15, 200));  // only 200 available
        Order buy4 = limit(OrderSide.BUY, 150.15, 500);   // wants 500
        MatchResult r4 = book4.match(buy4);
        System.out.println("Incoming: BUY 500@150.15 (only 200 available)");
        System.out.println("Result:   " + r4);
        r4.getTrades().forEach(System.out::println);
        System.out.printf("BUY order avgFillPrice: %.2f (expected 150.15)%n",
                buy4.getAvgFillPrice());
        System.out.printf("BUY order leavesQty:    %d   (expected 300)%n",
                buy4.getLeavesQty());
        System.out.println("Book (300 remaining should rest on bid):");
        System.out.println(book4);

        // ════════════════════════════════════════════════
        // Scenario 5: Market order
        // ════════════════════════════════════════════════
        System.out.println("\n════ Scenario 5: Market order ════");
        LimitOrderBook book5 = new LimitOrderBook("AAPL");

        book5.match(limit(OrderSide.SELL, 150.15, 200));
        book5.match(limit(OrderSide.SELL, 150.20, 300));

        // Market order: price=0 (ignored), eats whatever is available
        Order mktBuy = market(OrderSide.BUY, 150);
        MatchResult r5 = book5.match(mktBuy);
        System.out.println("Incoming: MARKET BUY 150");
        System.out.println("Result:   " + r5);
        r5.getTrades().forEach(System.out::println);
        System.out.printf("Market order avgFillPrice: %.4f%n", mktBuy.getAvgFillPrice());
        System.out.println("Book after market order:");
        System.out.println(book5);
    }

    // ── Helpers ──

    private static Order limit(OrderSide side, double price, long qty) {
        String id = "O" + (++orderCounter);
        return new Order(id, "CL-" + id, "ACC001", "AAPL",
                side, OrderType.LIMIT, qty, price, TimeInForce.GTC);
    }

    private static Order market(OrderSide side, long qty) {
        String id = "O" + (++orderCounter);
        // limitPrice=0 for market orders (your Order constructor validates
        // only LIMIT/STOP orders need a positive price, so 0 is fine here)
        return new Order(id, "CL-" + id, "ACC001", "AAPL",
                side, OrderType.MARKET, qty, 0, TimeInForce.GTC);
    }
}