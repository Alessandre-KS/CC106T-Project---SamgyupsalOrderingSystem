package com.example.samgyupsalorderingsystem;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.*;

public class BacksiteOrderTest {

    @Test
    public void testOrderLifecycle() {
        BacksiteOrder order = new BacksiteOrder("ORD-001", "Table 1", "SET MEAL B",
                Arrays.asList("2x Plain Pork Belly", "1x Cheese Dip"));

        // Step 1: Initial state is PREPARING (Chefs see order and prepare it)
        assertTrue(order.isPreparing());
        assertFalse(order.isReadyForPickup());
        assertFalse(order.isServed());
        assertEquals("Table 1", order.getTableCode());
        assertEquals("SET MEAL B", order.getSetMeal());

        // Step 2: Chef finishes cooking and marks ready for pickup
        order.markReady();
        assertFalse(order.isPreparing());
        assertTrue(order.isReadyForPickup());
        assertFalse(order.isServed());

        // Step 3: Floor staff picks up food and delivers to customer
        order.markServed();
        assertFalse(order.isPreparing());
        assertFalse(order.isReadyForPickup());
        assertTrue(order.isServed());
        assertEquals(BacksiteOrder.Status.SERVED, order.getStatus());
    }

    @Test
    public void testAddonOrder() {
        // Customer placed initial order, then realized they forgot an extra cut of pork
        BacksiteOrder initialOrder = new BacksiteOrder("ORD-001", "Table 1", "SET MEAL B",
                Arrays.asList("2x Plain Pork Belly", "1x Cheese Dip"), false);
        assertFalse(initialOrder.isAddon());
        assertEquals("INITIAL ORDER", initialOrder.getOrderTypeLabel());

        // New separate add-on ticket created
        BacksiteOrder addonOrder = new BacksiteOrder("ORD-003", "Table 1", "SET MEAL B",
                Arrays.asList("1x Spicy Pork"), true);
        assertTrue(addonOrder.isAddon());
        assertEquals("ADD-ON", addonOrder.getOrderTypeLabel());
        assertTrue(addonOrder.isPreparing());
    }

    @Test
    public void testHeadCountPax() {
        BacksiteOrder order = new BacksiteOrder("ORD-001", "Table 1", 4, "SET MEAL B",
                Arrays.asList("2x Plain Pork Belly"), false);
        assertEquals(4, order.getHeadCount());
        assertEquals("Table 1 (4 Pax)", order.getTableDisplay());

        // Default pax fallback
        BacksiteOrder defaultOrder = new BacksiteOrder("ORD-002", "Table 2", "SET MEAL A",
                Arrays.asList("1x Plain Pork Belly"));
        assertEquals(2, defaultOrder.getHeadCount());
        assertEquals("Table 2 (2 Pax)", defaultOrder.getTableDisplay());
    }
}
