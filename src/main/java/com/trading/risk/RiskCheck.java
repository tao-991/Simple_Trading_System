package com.trading.risk;

import com.trading.model.Order;

/**
 * Contract for a single pre-trade risk rule.
 *
 * Each concrete rule (OrderSizeCheck, PositionLimitCheck, PriceBandCheck, ...)
 * implements this interface. RiskEngine holds a List<RiskCheck> and runs them
 * in order, short-circuiting on the first failure.
 *
 * Implementation contract (rules every check MUST obey):
 * 1. Return a result, never null - always RiskCheckResult.pass() or .fail().
 * 2. Never mutate the order. A check only INSPECTS; it does not change state.
 * 3. Do not throw for a business rejection. An over-limit order is a fail() result,
 * not an exception. Exceptions are reserved for genuine errors (null input, Redis unreachable, etc.).
 * */


@FunctionalInterface
public interface RiskCheck {

    /**
     * Evaluate this rule against an incoming order.
     *
     * @param order the order to check (never null)
     * @return a passing or failing verdict (never null)
     * */
    RiskCheckResult check(Order order);

    /**
     * Human-readable name for logging and diagnostics.
     * Defaults to the class's simple name, e.g. "PositionLimitCheck",
     * so a rejection log can say WHICH rule rejected the order.
     * */

    default String name(){
        return getClass().getSimpleName();
    }

}
