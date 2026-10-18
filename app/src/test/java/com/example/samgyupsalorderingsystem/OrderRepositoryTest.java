package com.example.samgyupsalorderingsystem;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public class OrderRepositoryTest {

    private OrderRepository repo;

    @Before
    public void setUp() {
        repo = OrderRepository.getInstance();
        repo.clearAll();
    }

    @Test
    public void testCleanStateAtStart() {
        assertEquals("Repository should start completely empty", 0, repo.getAllOrders().size());
        assertEquals(0, repo.getPreparingOrders().size());
        assertEquals(0, repo.getReadyOrders().size());
        assertEquals(0, repo.getServedOrders().size());
    }

    @Test
    public void testOrderCreationAndLifecycle() {
        // Customer places order for Table 1
        BacksiteOrder order = repo.createOrder("Table 1", 4, "SET MEAL B",
                Arrays.asList("2x Plain Pork Belly", "1x Cheese Dip"), false);

        assertNotNull(order);
        assertEquals("ORD-Table1-001", order.getOrderId());
        assertEquals("Table 1", order.getTableCode());
        assertEquals(4, order.getHeadCount());
        assertFalse(order.isAddon());

        // Chef station sees 1 preparing order
        List<BacksiteOrder> preparing = repo.getPreparingOrders();
        assertEquals(1, preparing.size());
        assertEquals("ORD-Table1-001", preparing.get(0).getOrderId());

        // Chef marks ready for pickup
        order.markReady();
        assertEquals(0, repo.getPreparingOrders().size());
        List<BacksiteOrder> ready = repo.getReadyOrders();
        assertEquals(1, ready.size());
        assertEquals("ORD-Table1-001", ready.get(0).getOrderId());

        // Floor staff delivers to table
        order.markServed();
        assertEquals(0, repo.getReadyOrders().size());
        assertEquals(1, repo.getServedOrders().size());
    }

    @Test
    public void testClearAllDatabaseWipe() {
        repo.createOrder("Table 1", 2, "SET MEAL A", Arrays.asList("1x Plain Pork"), false);
        repo.createOrder("Table 2", 4, "SET MEAL C", Arrays.asList("2x Beef Bulgogi"), false);
        assertEquals(2, repo.getAllOrders().size());

        // Clean database
        repo.clearAll();
        assertEquals(0, repo.getAllOrders().size());
        assertEquals(0, repo.getPreparingOrders().size());

        // Next created order starts at ORD-Table1-001 again
        BacksiteOrder freshOrder = repo.createOrder("Table 1", 2, "SET MEAL A", Arrays.asList("1x Pork"), false);
        assertEquals("ORD-Table1-001", freshOrder.getOrderId());
    }

    @Test
    public void testCheckoutTable() {
        BacksiteOrder o1 = repo.createOrder("Table 1", 2, "SET MEAL A", Arrays.asList("1x Pork"), false);
        BacksiteOrder o2 = repo.createOrder("Table 1", 2, "SET MEAL A", Arrays.asList("1x Kimchi"), true);
        assertFalse(repo.isTableCheckedOut("Table 1"));

        repo.checkoutTable("Table 1");
        assertTrue(repo.isTableCheckedOut("Table 1"));
        assertTrue(o1.isCheckedOut());
        assertTrue(o2.isCheckedOut());
    }

    @Test
    public void testCreateOrderWithIdAndHasInitialOrder() {
        String table = "Table 3";
        assertFalse(repo.hasInitialOrder(table));

        BacksiteOrder o = repo.createOrderWithId("ORD-999", table, 2, "SET MEAL A", Arrays.asList("1x Pork"), false);
        assertEquals("ORD-999", o.getOrderId());
        assertTrue(repo.hasInitialOrder(table));

        // Re-adding with same ID returns existing
        BacksiteOrder existing = repo.createOrderWithId("ORD-999", table, 2, "SET MEAL A", Arrays.asList("1x Pork"), false);
        assertSame(o, existing);

        // Checkout clears active initial order
        repo.checkoutTable(table);
        assertFalse(repo.hasInitialOrder(table));
    }
}
