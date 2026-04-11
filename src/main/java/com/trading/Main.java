package com.trading;

import com.trading.common.OrderStatus;
import com.trading.common.TimeInForce;
import com.trading.common.OrderType;
import com.trading.common.OrderSide;
import com.trading.model.Order;


public class Main {
    static void main() {

        System.out.println("=== Trading System - OMS Demo  ===\n");

        // --- Step 1: Create a new limit order  ---
        // Scenario: Account ACC-001 wants to buy 100 shares of APPL at $150.00
        Order order = new Order(
               "ORD-001",
               "CLIENT-ORD-001",
               "ACC-001",
               "APPL",
               OrderSide.BUY,
               OrderType.LIMIT,
               100,
               150.00,
               TimeInForce.GTC
        );

        System.out.println("Order Created:");
        System.out.println(order);
        System.out.println();

        // --- Step 2: Simulate a partial fill  ---
        // Seller A has 40 shares @150.00 -> first partial fill
        System.out.println("Seller A fills 40 shares @150.00:");
        order.applyFill(40, 150.00);
        System.out.println(order);
        System.out.println();

        // --- Step 3: Simulate another partial fill ---
        // Seller B has 35 shares @149.90 -> second partial fill
        System.out.println("Seller B fills 35 shares @149.90:");
        order.applyFill(35, 149.90);
        System.out.println(order);
        System.out.println();

        // --- Step 4: Simulate the final fill ---
        // Seller C has 25 shares @149.95 -> order fully filled
        System.out.println("Seller C fills 25 shares @149.95:");
        order.applyFill(25, 149.95);
        System.out.println(order);
        System.out.println();

        // --- Step 5: Try to cancel a fully filled order ---
        // This should throw an exception
        System.out.println("Attempting to cancel a fully filled order...");
        try{
            order.cancel();
        } catch (IllegalStateException e) {
            System.out.println("Caught expected exception: " + e.getMessage());
        }
        System.out.println();


        // ── Step 6: Create a second order and cancel it ──
        // Scenario: a different order that never gets filled
        Order order2 = new Order(
                "ORD-002",
                "CLIENT-ORD-002",
                "ACC-001",
                "TSLA",
                OrderSide.SELL,
                OrderType.LIMIT,
                200,
                250.00,
                TimeInForce.GTC
        );

        System.out.println("Second order created:");
        System.out.println(order2);
        System.out.println();

        System.out.println("Cancelling second order:");
        order2.cancel();
        System.out.println(order2);
        System.out.println();

        // ── Step 7: Create a third order and reject it ──
        // Scenario: risk engine rejects the order
        Order order3 = new Order(
                "ORD-003",
                "CLIENT-ORD-003",
                "ACC-002",
                "AAPL",
                OrderSide.BUY,
                OrderType.MARKET,
                10000,  // large quantity, risk engine would reject this
                0, // no limit price for market orders
                TimeInForce.IOC
        );

        System.out.println("Third order created (larget market order):");
        System.out.println(order3);
        System.out.println();

        System.out.println("Risk engine rejects the order:");
        order3.reject();
        System.out.println(order3);


        // ── Step 8: Test validation ──
        System.out.println("=== Validation Tests ===\n");

        // Test 1: negative quantity
        try{
            Order badOrder = new Order(
                    "Ord-999",
                    "CLIENT-ORD-999",
                    "ACC-001",
                    "AAPL",
                    OrderSide.BUY,
                    OrderType.LIMIT,
                    -100,
                    150.00,
                    TimeInForce.GTC
            );
        } catch (IllegalArgumentException e) {
            System.out.println("Caught expected error: " + e.getMessage());
        }

        // Test 2: limit order with no price
        try{
            Order badOrder2 = new Order(
                    "ORD-998",
                    "CLIENT-ORD-998",
                    "ACC-001",
                    "AAPL",
                    OrderSide.BUY,
                    OrderType.LIMIT,
                    100,
                    0,  // invalid: limit order needs a price
                    TimeInForce.GTC
            );
        } catch (IllegalArgumentException e) {
            System.out.println("Caught expected error: " + e.getMessage());
        }

        //Test 3: market order with no price -> should be fine
        Order marketOrder = new Order(
                "ORD-997",
                "CLIENT-ORD-997",
                "ACC-001",
                "AAPL",
                OrderSide.BUY,
                OrderType.MARKET,
                100,
                0, // valid: market orders dont need a price
                TimeInForce.GTC
        );
        System.out.println("Market order created successfully: " + marketOrder);


    }
}
