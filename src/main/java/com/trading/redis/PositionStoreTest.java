package com.trading.redis;

import java.util.Map;

public class PositionStoreTest {

    public static void main(String[] args) {

        PositionStore store = new PositionStore("localhost", 6379);

        System.out.println("=== PositionStore Smoke Test ===\n");

        // Step 1: write a position
        store.updatePosition("ACC-001", "AAPL", 400, 150.15, 0.0);
        System.out.println("Written: ACC-001 AAPL netQty=400 avgCost=150.15");

        // Step 2: read back a single field
        long netQty = store.getNetQty("ACC-001", "AAPL");
        System.out.println("getNetQty: " + netQty + "  (expected 400)");

        // Step 3: read all fields
        Map<String, String> pos = store.getPosition("ACC-001", "AAPL");
        System.out.println("getPosition: " + pos);

        // Step 4: update just netQty, verify avgCostPrice unchanged
        store.updatePosition("ACC-001", "AAPL", 600, 151.00, 200.0);
        System.out.println("\nUpdated: netQty=600 avgCost=151.00 realizedPnL=200.0");
        System.out.println("getPosition after update: " + store.getPosition("ACC-001", "AAPL"));

        // Step 5: account with no position - should return 0, not crash
        long missing = store.getNetQty("ACC-999", "TSLA");
        System.out.println("\ngetNetQty for unknown account: " + missing + "  (expected 0)");

        store.close();
        System.out.println("\nDone.");
    }
}