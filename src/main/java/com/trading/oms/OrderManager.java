package com.trading.oms;

import com.trading.matching.Trade;
import com.trading.model.Order;
import com.trading.common.OrderStatus;

import java.util.List;

/**
 * Brides the Matching Engine and the OMS layer.
 *
 * Receives Trade events from the MatchingEngine and applies them
 * to the corresponding Order objects in the OrderStore.
 *
 * Threading note: onTrade() is called on the symbol's matching thread.
 * OrderStore uses ConcurrentHashMap so cross-thread reads are safe.
 * Each Order's applyFill() is synchronized, so concurrent fills on
 * different thread are also safe.
 * **/

public class OrderManager {
    private final OrderStore orderStore;

    public OrderManager(OrderStore orderStore) {
        this.orderStore = orderStore;
    }


    /**
     * Process a list of trades produced by one matching cycle.
     * Called by MatchingEngine's tradeHandler after each match() call.
     * **/

    public void onTrades(List<Trade> trades){
        for (Trade trade : trades){
            applyTrade(trade);
        }
    }

    public void applyTrade(Trade trade) {
        // Apply fill to the incoming order
        applyFillToOrder(trade.getIncomingOrderId(), trade.getMatchedQty(), trade.getTradePrcie());

        // Apply fill to the resting order
        applyFillToOrder(trade.getRestingOrderId(), trade.getMatchedQty(), trade.getTradePrcie());

        System.out.printf("[OrderManager] Trade applied: %s%n", trade);
    }

    private void applyFillToOrder(String orderId, long matchedQty, double tradePrice){
        Order order = orderStore.findActiveById(orderId);

        if(order==null){
            // This can happen if the order aws already archived (race condition between a cancel and a fill). Safe to ignore - log and move on
            System.err.printf("[OrderManager] WARNING: Order %s not found in active store " + "- may have been cancled concurrently%n", orderId);

            return;
        }

        // applyFill() is synchronized on the Order Object - thread safe
        order.applyFill(matchedQty, tradePrice);

        System.out.printf("[OrderManager] Order %s updated -> status=%s filledQty=%d leavesQty=%d avgPx=%.4f%n",
                orderId,
                order.getStatus(),
                order.getFilledQty(),
                order.getLeavesQty(),
                order.getAvgFillPrice());

        // If terminal state reached, move to archive
        if (order.getStatus() == OrderStatus.FILLED){
            orderStore.archive(orderId);
            System.out.printf("[OrderManger] Order %s FILLED -> archived%n", orderId);
        }


    }


}
