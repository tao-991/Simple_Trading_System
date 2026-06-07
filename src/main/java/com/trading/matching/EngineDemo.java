package com.trading.matching;

import com.trading.common.OrderSide;
import com.trading.common.OrderType;
import com.trading.common.TimeInForce;
import com.trading.model.Order;
import com.trading.oms.OrderManager;
import com.trading.oms.OrderStore;

import java.util.concurrent.atomic.AtomicInteger;

public class EngineDemo {

    private static final AtomicInteger orderCounter = new AtomicInteger(0);

    public static void main(String[] args) throws InterruptedException {

        // ── Wire up the system ──
        OrderStore   orderStore   = new OrderStore();
        OrderManager orderManager = new OrderManager(orderStore);

        // MatchingEngine's tradeHandler now calls OrderManager instead of just printing
        MatchingEngine engine = new MatchingEngine(
                trades -> orderManager.onTrades(trades)
        );

        engine.registerSymbol("AAPL");

        // ── Submit orders: first register them in OMS, then send to engine ──

        // Resting sell orders
        Order sell1 = limit("AAPL", OrderSide.SELL, 150.15, 300);
        Order sell2 = limit("AAPL", OrderSide.SELL, 150.20, 500);
        orderStore.add(sell1);
        orderStore.add(sell2);
        engine.submitOrder(sell1);
        engine.submitOrder(sell2);

        // Incoming buy: should cross both levels
        Order buy1 = limit("AAPL", OrderSide.BUY, 150.20, 400);
        orderStore.add(buy1);
        engine.submitOrder(buy1);

        // Wait for matching threads to finish
        Thread.sleep(300);

        // ── Print final state ──
        System.out.println("\n══════════════════════════════════");
        System.out.println("Final Order States:");
        System.out.println("══════════════════════════════════");

        // buy1 should be FILLED (400 = 300 + 100)
        printOrder("buy1",  orderStore.findAnyById(buy1.getOrderId()));

        // sell1 should be FILLED (300 fully consumed)
        printOrder("sell1", orderStore.findAnyById(sell1.getOrderId()));

        // sell2 should be PARTIALLY_FILLED (500 - 100 = 400 remaining)
        printOrder("sell2", orderStore.findAnyById(sell2.getOrderId()));

        System.out.println("\nActive orders:   " + orderStore.activeOrderCount());
        System.out.println("Archived orders: " + orderStore.archivedOrderCount());

        engine.shutdown();
    }

    private static void printOrder(String label, Order order) {
        if (order == null) {
            System.out.println(label + ": NOT FOUND");
            return;
        }
        System.out.printf("%s: status=%-18s filled=%d leaves=%d avgPx=%.4f%n",
                label,
                order.getStatus(),
                order.getFilledQty(),
                order.getLeavesQty(),
                order.getAvgFillPrice());
    }

    private static Order limit(String symbol, OrderSide side, double price, long qty) {
        String id = symbol + "-" + orderCounter.incrementAndGet();
        return new Order(id, "CL-" + id, "ACC001", symbol,
                side, OrderType.LIMIT, qty, price, TimeInForce.GTC);
    }
}