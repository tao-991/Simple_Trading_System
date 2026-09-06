package com.trading.risk;

import com.trading.common.OrderSide;
import com.trading.model.Order;
import com.trading.redis.PositionStore;

/**
 * Rejects an order if the account's net position, AFTER this orfer fills,
 * would exceed the configured limit for that symbol.
 *
 * Reads the CURRENT position from Redis (via PositionStore), then simulates
 * what the position would become if this order were fully filled/
 *
 * The limit is on the ABSOLUTE net position: being 1500 short is just as
 * risky as being 1500 long, so both directions are capped by the same value/
 *
 * NOTE - TOCTOU (time-of-check-to-time-of-use)
 * The position read here can be stale by the time this order actually fills,
 * because other orders for the same account may be in flight concurrently.
 * A production system would "reserve" the exposure at accept-time rather
 * than just checking it. This check is check-then-act  only.
 *
 * */

public class PositionLimitCheck implements RiskCheck {
    private final PositionStore positionStore;
    private final long positionLimit;

    public PositionLimitCheck(PositionStore positionStore, long positionLimit) {
        if (positionLimit <= 0){
            throw new IllegalArgumentException(
                    "positionLimit must be positive, got " + positionLimit
            );
        }
        this.positionStore = positionStore;
        this.positionLimit = positionLimit;
    }

    @Override
    public RiskCheckResult check(Order order){
        long currentPosition = positionStore.getNetQty(order.getAccountId(), order.getSymbol());

        long newPosition = (order.getSide() == OrderSide.BUY) ? currentPosition + order.getQuantity() : currentPosition - order.getQuantity();

        if (Math.abs(newPosition) > positionLimit) {
            return RiskCheckResult.fail("POSITION_LIMIT_EXCEED");
        }

        return RiskCheckResult.pass();
    }

}
