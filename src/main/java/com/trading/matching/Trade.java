package com.trading.matching;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

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

    /**
     * Full constructor used ONLY by Jackson for deserialization.
     *
     * Unlike the 5-arg constructor, this one accepts tradeId and tradeTime
     * as parameters, so a Trade read back from Kafka keeps its ORIGINAL
     * tradeId and timestamp instead of generating new ones.
     *
     * @JsonCreator tells Jackson: "use THIS constructor to rebuild the object"
     * @JsonProperty maps each JSON field name to a constructor parameter
     * */
    @JsonCreator
    public Trade(
            @JsonProperty("tradeId") String tradeId,
            @JsonProperty("incomingOrderId") String incomingOrderId,
            @JsonProperty("restingOrderId") String restingOrderId,
            @JsonProperty("symbol") String symbol,
            @JsonProperty("matchedQty") long matchedQty,
            @JsonProperty("tradePrice") double tradePrice,
            @JsonProperty("tradeTime") long tradeTime
    ){
        this.tradeId = tradeId; // use the value from JSON
        this.incomingOrderId = incomingOrderId;
        this.restingOrderId = restingOrderId;
        this.symbol = symbol;
        this.matchedQty = matchedQty;
        this.tradePrice = tradePrice;
        this.tradeTime = tradeTime; // use the value from JSON
    }

    public String getTradeId() { return tradeId;}
    public String getIncomingOrderId() {return incomingOrderId;}
    public String getRestingOrderId() {
        return restingOrderId;
    }
    public String getSymbol() {return symbol;}
    public long getMatchedQty() {return matchedQty;}
    public double getTradePrice(){ return tradePrice;}
    public long getTradeTime() { return tradeTime;}

    @Override
    public String toString() {
        return String.format(
                "[Trade %s | %s | incoming=%s vs resting=%s | qty=%d @ %.2f]",
                tradeId, symbol, incomingOrderId, restingOrderId, matchedQty, tradePrice
        );
    }
}
