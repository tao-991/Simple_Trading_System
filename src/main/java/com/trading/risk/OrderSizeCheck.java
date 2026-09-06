package com.trading.risk;

import com.trading.model.Order;

/**
 * Rejects any order whose quantity exceeds a configured maximum.
 *
 * This is the cheapest possible check: it only read a field off the
 * order itself, no external lookups. That's why it should run FIRST
 * in the RiskEngine's chain - kill an obviously oversized order before
 * paying for any network round-trip (e.g. PositionLinitCheck's Redis call).
 * */

public class OrderSizeCheck implements RiskCheck{
    private final long maxOrderSize;

    public OrderSizeCheck(long maxOrderSize){
        if (maxOrderSize <= 0){
            throw new IllegalArgumentException(
                    "maxOrderSize must be positive, got " + maxOrderSize
            );
        }
        this.maxOrderSize = maxOrderSize;
    }

    @Override
    public RiskCheckResult check(Order order){
        if (order.getQuantity() > maxOrderSize){
            return RiskCheckResult.fail("ORDER_SIZE_EXCEEDED");
        }
        return RiskCheckResult.pass();
    }
}
