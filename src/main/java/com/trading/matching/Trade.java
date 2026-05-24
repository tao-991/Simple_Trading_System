package com.trading.matching;

/**
 * Represents a single matched trade between two orders.
 *
 * One Trade is generated for each resting order (or partial resting order)
 * that an incoming order consumes. A single incoming order crossing multiple
 * price levels will produce multiple Trade objects.
 *
 * Immutable by deisgn - a trade record should never change after creation.
 * **/

public class Trade {

    private final String tradeId;
    private final String incomingOrderId;
    private final String restingOrderId;
    private final String symbol;
    private final long matchedQty;
    private final double tradePrice; // always te resting order's limit price
    private final long tradeTime; // nanosecond timestamp

    // Simple counter for generating trade IDs in this demo.
    // Production systems use a distributed sequence generator.
    private static long tradeCounter = 0;

    public Trade(String incomingOrderId, String restingOrderId,
                 String symbol, long matchedQty, double tradePrice){
        this.tradeId = "T-" + (++tradeCounter);
        this.incomingOrderId = incomingOrderId;
        this.restingOrderId = restingOrderId;
        this.symbol = symbol;
        this.matchedQty = matchedQty;
        this.tradePrice = tradePrice;
        this.tradeTime = System.nanoTime();
    }

    public String getTradeId() { return tradeId;}
    public String getIncomingOrderId() {return incomingOrderId;}
    public String getRestingOrderId() {
        return restingOrderId;
    }
    public String getSymbol() {return symbol;}
    public long getMatchedQty() {return matchedQty;}
    public double getTradePrcie(){ return tradePrice;}
    public long getTradeTime() { return tradeTime;}

    @Override
    public String toString() {
        return String.format(
                "[Trade %s | %s | incoming=%s vs resting=%s | qty=%d @ %.2f]",
                tradeId, symbol, incomingOrderId, restingOrderId, matchedQty, tradePrice
        );
    }
}
