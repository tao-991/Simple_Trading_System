package com.trading.matching;

import com.trading.model.Order;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

/**
 * Represents all resting orders at a single price point,
 * maintained in FIFO (time priority) order.
 *
 * Threading model: This class is NOT thread-safe. It is designed to be
 * accessed only by the single matching thread that ownds the parent
 * LimitOrderBook (Single Writer Principle).
 *
 * **/

public class PriceLevel {

    private final long priceTicks; // price in ticks, since the price in order is double but in here is long (e.g., 15015 = $150.15)
    private final Deque<Order> orders = new ArrayDeque<>();
    private long totalQty; // aggregate leavesQty of all orders at this level

    public PriceLevel(long priceTicks) {
        this.priceTicks = priceTicks;
        this.totalQty = 0;
    }

    /**
     * Add an order to the tail of the FIFO queue.
     * Called when a limit order rests on this price level
     * **/
    public void addOrder(Order order) {
        orders.addLast(order);
        totalQty += order.getLeavesQty();
    }

    /**
     * Remove an order from this level. O(n) within the level - will be optimized later.
     * Returns true if the order was found and removed.
     * **/
    public boolean removeOrder(Order order) {
        boolean removed = orders.remove(order);
        if (removed) {
            totalQty -= order.getLeavesQty();
        }
        return removed;
    }

    /**
     * Peek at the earliest order without removing it.
     * Used by matching engine to find the next counterparty
     * **/
    public Order peekFirst(){
        return orders.peekFirst();
    }

    /**
     * Poll (remove and return) the earliest order.
     * Called when an order at the head of this level gets fully filled.
     * **/
    public Order pollFirst() {
        Order removed = orders.pollFirst();
        if (removed != null) {
            totalQty -= removed.getLeavesQty();
        }
        return removed;
    }

    /**
     * Decrement totalQty when a resting order is partially filled.
     * The order stays at the head of the queue (time priority preserved).
     * The caller is responsible for also calling order.applyFill()
     * **/
    public void onPartialFill(long filledQty) {
        totalQty -= filledQty;
    }

    public boolean isEmpty(){
        return orders.isEmpty();
    }

    public int orderCount() {
        return orders.size();
    }

    public long getPriceTicks(){
        return priceTicks;
    }

    public long getTotalQty(){
        return totalQty;
    }

    // Read-only iteration (for market data snapshot / debugging).
    public Iterator<Order> iterator() {
        return orders.iterator();
    }


}
