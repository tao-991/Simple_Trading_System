package com.trading.model;

import com.trading.common.TimeInForce;
import com.trading.common.OrderSide;
import com.trading.common.OrderType;
import com.trading.common.OrderStatus;

public class Order {

    // --- Identity fields: final, never change after creation ---
    private final String orderId;
    private final String clOrder; // Client-generated order ID (IFX Tag 11)
    private final String accountId;
    private final String symbol;
    private final OrderSide side;
    private final OrderType orderType;
    private final long quantity; //Total order quantity
    private final double limitPrice; // 0 for Market Orders
    private final TimeInForce timeInForce;
    private final long createdAt; // Nanosecond timestamp

    // --- Mutable state: volatile, read/written by multiple thread  ---
    private volatile OrderStatus status;
    private volatile long filledQty; // Quantity filled so far
    private volatile long leavesQty; // Remaining unfilled quantity
    private volatile double avgFillPrice; // Volume-weighted average fill price
    private volatile long updatedAt; // Last updated timestamp

    // --- Constructor  ---
    public Order(String orderId, String clOrdId, String accountId,
                 String symbol, OrderSide side, OrderType orderType,
                 long quantity, double limitPrice, TimeInForce timeInForce) {

        validate(orderId, clOrdId, accountId, symbol, orderType, quantity, limitPrice);

        this.orderId = orderId;
        this.clOrder = clOrdId;
        this.accountId = accountId;
        this.symbol = symbol;
        this.side = side;
        this.orderType = orderType;
        this.quantity = quantity;
        this.limitPrice = limitPrice;
        this.timeInForce = timeInForce;
        this.createdAt = System.nanoTime();

        //Initial state
        this.status = OrderStatus.NEW;
        this.filledQty = 0;
        this.leavesQty = quantity;
        this.avgFillPrice = 0.0;
        this.updatedAt = this.createdAt;
    }

    // --- Private validation - called before object is constructed ---
    private static void validate(String orderId, String clOrdId, String accountId,
                          String symbol, OrderType orderType,
                          long quantity, double limitPrice) {
        // Identify fields must not be null or empty
        if (orderId == null || orderId.isBlank()) throw new IllegalArgumentException("orderId cannot be null or empty");
        if (clOrdId == null || clOrdId.isBlank()) throw new IllegalArgumentException("clOrdId cannot be null or empty");
        if (accountId == null || accountId.isBlank()) throw new IllegalArgumentException("accountId cannot be null or empty");
        if (symbol == null || symbol.isBlank()) throw new IllegalArgumentException("symbol cannot be null or empty");

        // Quantity must be positive
        if (quantity <= 0) throw new IllegalArgumentException("quantity must be positive, got " + quantity);

        // Limit orders must have a valid price
        // Market orders ignore limitPrice, so we only validate for LIMIT and STOP
        if (orderType == OrderType.LIMIT || orderType == OrderType.STOP) {
            if (limitPrice <= 0) throw new IllegalArgumentException("limitPrice must be positive for " + orderType +" orders, got: " + limitPrice);
        }

    }

    // --- Core business methods  ---

    /**
     * Apply a (partial or full) fill to this order.
     * synchronized: only one thread can modify state at a time.
     *
     * Example: 300 shares already filled @150, not fill 200 more @151
     * New avgFillPrice = (300*150 + 200*151) / 500 = 150.4
     *
     * **/

    public synchronized void applyFill(long matchedQty, double fillPrice) {

        // Recalculate volume-weighted average fill price
        this.avgFillPrice = (this.avgFillPrice * this.filledQty + matchedQty * fillPrice) / (this.filledQty + matchedQty);

        this.filledQty += matchedQty;
        this.leavesQty -= matchedQty;
        this.updatedAt = System.nanoTime();

        if (this.leavesQty == 0){
            this.status = OrderStatus.FILLED;
        } else {
            this.status = OrderStatus.PARTIALLY_FILLED;
        }
    }

    /**
     * Cancel this order.
     * A fully filled order cannot be canceled.
     * **/

    public synchronized void cancel() {
        if (this.status == OrderStatus.FILLED) {
            throw new IllegalStateException(
                    "Cannot cancel a fully filled order: " + orderId
            );
        }
        this.status = OrderStatus.CANCELED;
        this.updatedAt = System.nanoTime();
    }


    /**
     * Reject the order (e.g., failed pre-trade risk check).
     * **/
    public synchronized void reject() {
        this.status = OrderStatus.REJECTED;
        this.updatedAt = System.nanoTime();
    }


    // --- Getters (read-only, no setters by design)  ---
    public String getOrderId() {return orderId;}
    public String getClOrder() {return clOrder;}
    public String getAccountId() {return accountId;}
    public String getSymbol() {return symbol;}
    public OrderSide getSide() {return side;}
    public OrderType getOrderType() {return orderType;}
    public long getQuantity() {return quantity;}
    public double getLimitPrice() {return limitPrice;}
    public TimeInForce getTimeInForce() {return timeInForce;}
    public long getCreatedAt() {return createdAt;}
    public OrderStatus getStatus() {return status;}
    public long getFilledQty() {return filledQty;}
    public long getLeavesQty() {return leavesQty;}
    public double getAvgFillPrice() {return avgFillPrice;}
    public long getUpdatedAt() {return updatedAt;}

    // --- For debugging and logging ---
    @Override
    public String toString() {

        String priceDisplay = (orderType == OrderType.MARKET) ? String.format("MKT(avg=%.2f)", avgFillPrice) : String.format("@%.2f", limitPrice);

        return String.format(
                "[Order %s | %s %s %d%s | Status: %s | Filled: %d | Leaves: %d | AvgPrice: %.2f]",
                orderId, side, symbol, quantity, priceDisplay,
                status, filledQty, leavesQty, avgFillPrice
        );
    }
}
