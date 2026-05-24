package com.trading.matching;

import java.util.List;

/**
 * The result of processing one incoming order through the matching engine.
 *
 * Contains all trades generated and the final disposition of the incoming order.
 * **/

public class MatchResult {
    public MatchResult(List<Trade> trades, Disposition disposition, long remainingQty) {
        this.trades = trades;
        this.disposition = disposition;
        this.remainingQty = remainingQty;
    }

    public enum Disposition {
        FILLED, PARTIALLY_FILLED, RESTED, REJECTED
    }

    private final List<Trade> trades;
    private final Disposition disposition;
    private final long remainingQty;

    public List<Trade> getTrades() {return trades;}
    public Disposition getDisposition() {return disposition;}
    public long getRemainingQty() {return remainingQty;}
    public boolean hasTraded() {return !trades.isEmpty();}

    @Override
    public String toString() {
        return String.format(
                "[MatchResult | %s | trades=%d | remainingQty=%d]",
                disposition, trades.size(), remainingQty
        );
    }

}
