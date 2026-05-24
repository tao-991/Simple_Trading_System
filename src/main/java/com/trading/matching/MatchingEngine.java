package com.trading.matching;

import com.trading.common.OrderType;
import com.trading.model.Order;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Central Matching Engine.
 *
 * Manages one LimitOrderBook per symbol, each process by a dedicated
 * single-threaded ExecutorService (single Writer Principle).
 *
 * Thread safety model:
 *  - submitOrder() can be called from any thread (e.g. FIX Gateway thread, OMS thread)
 *  - All matching logic executes on the symbol's dedicated single thread
 *  - No locks needed inside LimitOrderBook or PriceLevel
 *
 * **/

public class MatchingEngine {

    // Symbol -> dedicated single-thread executor for that symbol's book
    private final ConcurrentHashMap<String, ExecutorService> executors = new ConcurrentHashMap<>();

    // symbol -> its LimitOrderBook
    private final ConcurrentHashMap<String, LimitOrderBook> books = new ConcurrentHashMap<>();

    // Callback: what to do after matching produces trades.
    // In production this would publish to Kafka. For now, caller injects a handler.
    private final Consumer<List<Trade>> tradeHandler;


    public MatchingEngine(Consumer<List<Trade>> tradeHandler) {
        this.tradeHandler = tradeHandler;
    }

    /**
     * Register a symbol before any orders are submited.
     * Safe to call from any thread; idempotent.
     * **/
    public void registerSymbol(String symbol){
        // computeIfAbsent is atomic in ConcurrentHashMap - no duplicate creation
        books.computeIfAbsent(symbol, LimitOrderBook::new);

        // Line 2: create a dedicated single-thread executor for this symbol
        executors.computeIfAbsent(symbol,s -> Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "matcher-"+ s); // name the read for debugging
            t.setDaemon(true);
            return t;
        }));
    }

    /**
     * Submit an order for matching. Returns immediately (non-blocking).
     *
     * The actual matching happens asynchronously on the symbol's dedicated thread.
     * Results are delivered via the tradeHandler callback (also on that thread).
     *
     * @throws IllegalArgumentException if the symbol has not been registered.
     *
     * **/
    public void submitOrder(Order order){
        String symbol = order.getSymbol();

        ExecutorService executor = executors.get(symbol);
        LimitOrderBook book = books.get(symbol);

        if (executor == null || book == null){
            throw new IllegalArgumentException(
              "Symbol not registered: " + symbol + ". Call registerSymbol() first."
            );
        }

        // Hand off to the symbol's single thread. Returns immediately.
        executor.submit(() -> {
            try{
                MatchResult result = book.match(order);
                if (result.hasTraded()) {
                    tradeHandler.accept(result.getTrades());
                }
            } catch (Exception e) {
                // In production: alert, dead-letter queue, etc.
                System.err.println("[MatchingEngine] Error processing order "
                        + order.getOrderId() + ": " + e.getMessage());
                e.printStackTrace();
            }
        });

    }

    /**
     * Best bid price for a symbol, or NaNif no bids.
     * Note: this is a snapshot read from a different thread - for display / monitoring only.
     * **/
    public double getBestBid(String symbol) {
        LimitOrderBook book = books.get(symbol);
        return book == null ? Double.NaN : book.getBestBid();
    }

    public double getBestAsk(String symbol) {
        LimitOrderBook book = books.get(symbol);
        return book == null ? Double.NaN : book.getBestAsk();
    }

    /**
     * Graceful shutdown: wait for all in-flight orders to finish matching.
     */
    public void shutdown() throws InterruptedException {
        for (ExecutorService ex : executors.values()) {
            ex.shutdown();
        }
        for (ExecutorService ex : executors.values()) {
            ex.awaitTermination(5, TimeUnit.SECONDS);
        }
        System.out.println("[MatchingEngine] Shutdown complete.");
    }

}
