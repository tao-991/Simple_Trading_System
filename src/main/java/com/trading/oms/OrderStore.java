package com.trading.oms;

import com.trading.model.Order;
import com.trading.common.OrderStatus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class OrderStore {

    // ━━━ Active store: orders still in play ━━━
    // Primary index: instant lookup by orderId
    // ConcurrentHashMap: thread-safe version of HashMap
    private final Map<String, Order> activeById = new ConcurrentHashMap<>();

    // Secondary index: all orders belonging to an account
    private final Map<String, List<Order>> activeByAccount = new ConcurrentHashMap<>();

    // Secondary index: all orders for a given symbol
    private final Map<String, List<Order>> activeBySymbol = new ConcurrentHashMap<>();


    // ━━━ Archive store: completed orders ━━━
    // TODO: Replace with PostgreSQL persistence (Phase 3)
    // Table: orders_archive
    // Columns: order_id, cl_ord_id, account_id, symbol, side, order_type,
    //          quantity, limit_price, time_in_force, status,
    //          filled_qty, avg_fill_price, created_at, updated_at
    // Index on: account_id, symbol, created_at
    private final Map<String, Order> archivedById
            = new ConcurrentHashMap<>();

    private final Map<String, List<Order>> archivedByAccount
            = new ConcurrentHashMap<>();

    private final Map<String, List<Order>> archivedBySymbol
            = new ConcurrentHashMap<>();


    // ━━━ Write operations ━━━

    /**
     * Add a new order to the active store.
     * Called when an order first enters the system.
     * All three should be sync
     */

    public void add(Order order) {
        // Primary index
        activeById.put(order.getOrderId(), order);

        // Secondary index: account
        // ComputeIfAbsent: "if this key has no list yet, create one, then add"
        activeByAccount.computeIfAbsent(order.getAccountId(), k -> new ArrayList<>()).add(order);

        // Secondary index: symbol
        activeBySymbol.computeIfAbsent(order.getSymbol(), k -> new ArrayList<>()).add(order);
    }




    /**
     * Move a completed order from active store to archive store.
     * Called when an order reaches a terminal state:
     * FILLED, CANCELED, or REJECTED.
     *
     * This keeps the active store lean and fast for Risk Engine queries.
     */

    public void archive(String orderId) {

        // computeIfPresent is atomic — it only executes if the key exists
        // and removes + returns the order in one atomic operation
        // This prevents two threads from both seeing the order as "present"

        // Step 1: FIND the order first, do NOT remove yet
        Order order = activeById.get(orderId);
        if (order == null) return; // already archived or never existed

        // Step 2: Validate BEFORE touching anything
        if (order.getStatus() != OrderStatus.FILLED
                && order.getStatus() != OrderStatus.CANCELED
                && order.getStatus() != OrderStatus.REJECTED) {
            throw new IllegalStateException(
                    "Cannot archive an order that is still active: "
                            + orderId + " status=" + order.getStatus()
            );
        }

        // Step 3: Validation passed - now safe to remove from all active indexes


        // To make sure one order will not be removed and added to archive twice. We need to make sure atomicity.
        // remove() on ConcurrentHashMap is atomic
        // If two threads race here, only ONE will get true, the others get False
        boolean removed = activeById.remove(orderId, order);
        if (!removed) return; // other thread already archived it, we lost the race

        List<Order> accountOrders = activeByAccount.get(order.getAccountId());
        if (accountOrders != null) accountOrders.remove(order);

        List<Order> symbolOrders = activeBySymbol.get(order.getSymbol());
        if (symbolOrders != null) symbolOrders.remove(order);

        // Step 4: Add to archive indexes
        archivedById.put(order.getOrderId(), order);

        archivedByAccount.computeIfAbsent(order.getAccountId(), k -> new ArrayList<>()).add(order);

        archivedBySymbol.computeIfAbsent(order.getSymbol(), k -> new ArrayList<>()).add(order);


    }

    // ━━━ Active store queries ━━━

    public Order findActiveById(String orderId) {
        return activeById.get(orderId);
    }

    public List<Order> findActiveByAccount(String accountId) {
        return activeByAccount.getOrDefault(accountId, Collections.emptyList());
    }

    public List<Order> findActiveBySymbol(String symbol) {
        return activeBySymbol.getOrDefault(symbol, Collections.emptyList());
    }

    public int activeOrderCount() {
        return activeById.size();
    }

    // ━━━ Archive store queries ━━━

    public Order findArchivedById(String orderId) {
        return archivedById.get(orderId);
    }

    public List<Order> findArchivedByAccount(String accountId) {
        return archivedByAccount.getOrDefault(accountId, Collections.emptyList());
    }

    public List<Order> findArchivedBySymbol(String symbol) {
        return archivedBySymbol.getOrDefault(symbol, Collections.emptyList());
    }

    public int archivedOrderCount() {
        return archivedById.size();
    }




    // ━━━ Cross-store queries (for reporting) ━━━

    /**
     * Find an order regardless of whether it is active or archived.
     * Used by reporting and audit tools.
     */

    public Order findAnyById(String orderId) {
        Order order = activeById.get(orderId);
        if (order == null){
            order = archivedById.get(orderId);
        }

        return order;
    }


}
