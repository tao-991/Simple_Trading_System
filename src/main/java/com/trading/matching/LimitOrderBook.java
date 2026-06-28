package com.trading.matching;

import com.trading.common.OrderSide;
import com.trading.common.OrderType;
import com.trading.model.Order;
import org.w3c.dom.ls.LSOutput;

import java.util.*;

/**
 * Pre-symbol Limit Order Book
 *
 * Maintains two sides (bids and asks), each as a price-stored map of PriceLevels.
 *
 * Threading model: Single Writer Principle. Each LimitOrderBook is owned
 * by exactly one matching thread; no synchronization is needed internally
 * **/
public class LimitOrderBook {

    // Minimum price increment in ticks. For equities with $0.01 tick, PRICE_SCALE = 100.
    private static final long PRICE_SCALE = 100;

    private final String symbol;

    // Bids: highest price first (reverseOrder). Best bid = firstEntry()
    private final TreeMap<Long, PriceLevel> bids = new TreeMap<>(Collections.reverseOrder());

    // Asks: lowest price first (natural order). Best Ask = firstEntry().
    private final TreeMap<Long, PriceLevel> asks = new TreeMap<>();

    // orderId -> Order index, for O(1) lookup on cancel.
    private final Map<String, Order> ordersById = new HashMap<>();

    public LimitOrderBook(String symbol) {
        this.symbol = symbol;
    }

    // ──────────────────────────────────────────────────────────────
    // Public API
    // ──────────────────────────────────────────────────────────────

    // ──────────────────────────────────────────────────────────────
    // ★ NEW: Core matching logic
    // ──────────────────────────────────────────────────────────────

    /**
     * Process an incoming order against the book.
     *
     * Rules (Price-Time Priority):
     *  1. Match against the best price on the opposite side first.
     *  2. Within a price level, match in FIFO order (time priority).
     *  3. Trade price = resting order's limit price (price maker wins).
     *  4. After matching, if the incoming order has remaining qty:
     *       - LIMIT order: rest in the book.
     *       - MARKET order: reject the remainder (cannot rest).
     *
     * @return MatchResult containing all generated trades and the final disposition.
     */

    public MatchResult match(Order incoming) {
        List<Trade> trades = new ArrayList<>();

        // Determine which side of the book to match against.
        // BUY incoming matches against ASK side, and vice versa.
        TreeMap<Long, PriceLevel> oppositeBook =
                incoming.getSide() == OrderSide.BUY ? asks : bids;

        // Track remaining qty locally - OrderManager will call applyFill() later
        long incomingRemaing = incoming.getLeavesQty();

        // -- Main matching loop --
        while (incomingRemaing > 0 && !oppositeBook.isEmpty()) {

            // Best price on the opposite side (ask: lowest price; bid: highest price)
            Map.Entry<Long, PriceLevel> bestEntry = oppositeBook.firstEntry();
            long bestPriceTicks = bestEntry.getKey();
            double bestPrice = toPrice(bestPriceTicks);

            // -- Price check: can we match at this level? --
            if(!isPriceMatch(incoming, bestPrice)){
                break;
            }

            PriceLevel level = bestEntry.getValue();
            Order resting = level.peekFirst(); // earliest order at this level

            // Safety  check - should not happen if book is consistent
            if (resting == null) {
                oppositeBook.remove(bestPriceTicks);
                continue;
            }

            long restingRemaining = resting.getLeavesQty();


            // -- Calcualte matched quantity --
            long matchQty = Math.min(incomingRemaing, restingRemaining);

            // Trade price is always the resting order's price (price maker wins)
            double tradePrice = resting.getLimitPrice();

            // -- Generate trade record --
            Trade trade = new Trade(incoming.getOrderId(), resting.getOrderId(), symbol, matchQty, tradePrice, incoming.getAccountId(), incoming.getSide(), resting.getAccountId(), resting.getSide());
            trades.add(trade);

//            // -- Apply fill to BOTH orders --
//            incoming.applyFill(matchQty, tradePrice);
//            resting.applyFill(matchQty, tradePrice);
            // Update book structure only
            incomingRemaing -= matchQty;
            long restingLeft = restingRemaining - matchQty;

            // -- Update PriceLevel and book structure --
            if (restingLeft == 0) {
                // resting order fully filled: remove it from queue and index
                level.pollFirst();
                ordersById.remove(resting.getOrderId());
                if (level.isEmpty()) {
                    oppositeBook.remove(bestPriceTicks);
                }
            } else {
                // Resting order partially filled: stays at queue head (time priority)
                // just update the level totalQty
                level.onPartialFill(matchQty);
            }

            // Loop continues: incoming may still have remaining qty to match
        }

        // --- Determine disposition and handle remainder --
        return buildResult(incoming, trades, incomingRemaing);
    }

    /**
     * Determine if the incoming order's price is acceptable for the best price on the opposite side.
     *
     * BUY: incoming.limitPrice >= bestAsk (willing to pay at least the ask)
     * SELL: incoming.limitPrice <= bestBid (willing to accept at most the bid)
     *
     * Market orders always match regardless of price.
     * **/

    private boolean isPriceMatch(Order incoming, double bestOppositePrice) {
        if (incoming.getOrderType() == OrderType.MARKET) {
            return true; // market orders accept any price
        }
        if (incoming.getSide() == OrderSide.BUY) {
            return incoming.getLimitPrice() >= bestOppositePrice;
        } else{
            return incoming.getLimitPrice() <= bestOppositePrice;
        }
    }

    /**
     * Build the MatchResult based on the outcome of the matching loop.
     * Also handles resting the remaining qty for limit orders.
     * **/
    private MatchResult buildResult(Order incoming, List<Trade> trades, long incomingRemaining) {
        if (incomingRemaining == 0) {
            return new MatchResult(trades, MatchResult.Disposition.FILLED, 0);
        }

        if (incoming.getOrderType() == OrderType.MARKET) {
            return new MatchResult(trades, MatchResult.Disposition.REJECTED, incomingRemaining);
        }

        // Limit order: rest the remainder in the book
        addOrder(incoming);

        if (trades.isEmpty()) {
            return new MatchResult(trades, MatchResult.Disposition.RESTED, incomingRemaining);
        } else {
            return new MatchResult(trades, MatchResult.Disposition.PARTIALLY_FILLED, incomingRemaining);
        }
    }

    /**
     * Add a limit order to the book.
     * Precondition: order.getOrderType() == LIMIT.
     */

    public void addOrder(Order order) {
        if (!order.getSymbol().equals(symbol)){
            throw new IllegalArgumentException(
                    "Order symbol " + order.getSymbol() + " does not match book " + symbol
            );
        }
        if (ordersById.containsKey(order.getOrderId())) {
            throw new IllegalStateException(
                    "Duplicate orderId in book: " + order.getOrderId()
            );
        }

        long priceTicks = toTicks(order.getLimitPrice());
        TreeMap<Long, PriceLevel> side = sideOf(order.getSide());

        // computeIfAbsent: create PriceLevel only if this is a new price point.
        PriceLevel level = side.computeIfAbsent(priceTicks, PriceLevel::new);
        level.addOrder(order);

        ordersById.put(order.getOrderId(), order);


    }

    /**
     * Cancel an order by orderId. Returns the removed Order, or null if not found.
     * The caller is responsible for updating order status via order.cancel()
     * **/

    public Order cancelOrder(String orderId) {
        Order order = ordersById.remove(orderId);
        if (order == null) {
            return null;
        }

        long priceTicks = toTicks(order.getLimitPrice());
        TreeMap<Long, PriceLevel> side = sideOf(order.getSide());
        PriceLevel level = side.get(priceTicks);

        if (level == null){
            // Invariant violation: ordersById and TreeMap out of sync.
            throw new IllegalStateException(
                    "Order " + orderId + " in index but price level missing"
            );
        }

        level.removeOrder(order);
        if (level.isEmpty()) {
            side.remove(priceTicks);
        }

        return order;

    }

    // ──────────────────────────────────────────────────────────────
    // Queries (for market data, risk engine, dashboard)
    // ──────────────────────────────────────────────────────────────

    /** Best bid price in dollars, or NaN if no bids. **/
    public double getBestBid() {
        Map.Entry<Long, PriceLevel> e = bids.firstEntry();
        return e == null ? Double.NaN : toPrice(e.getKey());
    }

    /** Best ask price in dollars, or NaN if no asks. **/
    public double getBestAsk() {
        Map.Entry<Long, PriceLevel> e = asks.firstEntry();
        return e == null ? Double.NaN : toPrice(e.getKey());
    }

    /** Spread = Best Ask - Best Bid, or NaN if one side is empty. */
    public double getSpread() {
        double bid = getBestBid();
        double ask = getBestAsk();
        return (Double.isNaN(bid) || Double.isNaN(ask)) ? Double.NaN : ask - bid;
    }

    /** Mid pirce = (Best Bid + Best Ask) / 2, or NaN if one side is empty. */
    public double getMidPrice(){
        double bid = getBestBid();
        double ask = getBestAsk();
        return (Double.isNaN(bid) || Double.isNaN(ask)) ? Double.NaN : (ask + bid) / 2.0;
    }

    public int getBidDepth() { return bids.size();}
    public int getAskDepth() { return asks.size();}
    public String getSymbol() { return symbol;}

    // ──────────────────────────────────────────────────────────────
    // Internal helpers
    // ──────────────────────────────────────────────────────────────

    private TreeMap<Long, PriceLevel> sideOf(OrderSide side) {
        return side == OrderSide.BUY ? bids : asks;
    }

    private long toTicks(double price) {
        return Math.round(price * PRICE_SCALE);
    }

    private static double toPrice(long ticks) {
        return ticks / (double) PRICE_SCALE;
    }

    // ──────────────────────────────────────────────────────────────
    // Debug: pretty-print the book
    // ──────────────────────────────────────────────────────────────
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("─── Order Book: ").append(symbol).append(" ───\n");
        sb.append(String.format("%-10s %-8s %-8s │ %-8s %-8s %-10s%n",
                "BidOrders", "BidQty", "BidPx", "AskPx", "AskQty", "AskOrders"));
        sb.append("──────────────────────────────┼──────────────────────────────\n");

        Iterator<Map.Entry<Long, PriceLevel>> bidIt = bids.entrySet().iterator();
        Iterator<Map.Entry<Long, PriceLevel>> askIt = asks.entrySet().iterator();

        // Show up to 5 levels on each side
        for (int i = 0; i < 5 && (bidIt.hasNext() || askIt.hasNext()); i++) {
            String bidStr;
            if (bidIt.hasNext()) {
                PriceLevel lvl = bidIt.next().getValue();
                bidStr = String.format("%-10d %-8d %-8.2f",
                        lvl.orderCount(), lvl.getTotalQty(), toPrice(lvl.getPriceTicks()));
            } else {
                bidStr = String.format("%-10s %-8s %-8s", "-", "-", "-");
            }

            String askStr;
            if (askIt.hasNext()) {
                PriceLevel lvl = askIt.next().getValue();
                askStr = String.format("%-8.2f %-8d %-10d",
                        toPrice(lvl.getPriceTicks()), lvl.getTotalQty(), lvl.orderCount());
            } else {
                askStr = String.format("%-8s %-8s %-10s", "-", "-", "-");
            }

            sb.append(bidStr).append(" │ ").append(askStr).append("\n");
        }
        return sb.toString();
    }
    

}
