package com.trading.risk;

/**
 * The result of a single pre-trade risk check.
 *
 * Immutable by design: once a verdict is produced it can be ever altered.
 * A "passed" result carries no reason; a "failed" result must carry one.
 * These two combinations are the ONLY valid states, which is why the
 * constructor is private and objects are built by passed/failed
 *
 * */

public final class RiskCheckResult {

    private final boolean passed;
    private final String reason; // null when result == passed

    private static final RiskCheckResult PASS = new RiskCheckResult(true, null);

    // Private: callers must use the pass()/failed() factory methods.
    private RiskCheckResult(boolean passed, String reason){
        this.passed = passed;
        this.reason = reason;
    }

    /** A failing result. Reason is mandatory (e.g., "POSITION_LIMIT_EXCEEDED*/
    public static RiskCheckResult pass() {
        return PASS;
    }


    public static RiskCheckResult fail(String reason) {
        if (reason == null || reason.isBlank()){
            throw new IllegalArgumentException("A failed risk check must have a reason");
        }
        return new RiskCheckResult(false, reason);
    }

    // --- Getters (read-only, no setters by design) ---
    public boolean isPassed() {return passed;}
    public String getReason() {return reason;}

    @Override
    public String toString(){
        return passed ? "PASS" : "FAIL: " + reason;
    }
}
