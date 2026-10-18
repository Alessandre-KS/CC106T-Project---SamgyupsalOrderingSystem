package com.example.samgyupsalorderingsystem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Central repository managing restaurant orders.
 * Starts completely fresh with 0 orders.
 * Supports adding real orders from MenuActivity and live state changes from StaffActivity.
 */
public class OrderRepository {

    private static OrderRepository instance;
    private final List<BacksiteOrder> orders = new CopyOnWriteArrayList<>();
    private final AtomicInteger orderCounter = new AtomicInteger(1);

    private OrderRepository() {}

    public static synchronized OrderRepository getInstance() {
        if (instance == null) {
            instance = new OrderRepository();
        }
        return instance;
    }

    public synchronized BacksiteOrder createOrder(String tableCode, int headCount, String setMeal, List<String> items, boolean isAddon) {
        String cleanTable = (tableCode != null && !tableCode.trim().isEmpty())
                ? tableCode.replaceAll("[^a-zA-Z0-9]", "")
                : "T1";
        String orderId = String.format(java.util.Locale.US, "ORD-%s-%03d", cleanTable, orderCounter.getAndIncrement());
        BacksiteOrder order = new BacksiteOrder(orderId, tableCode, headCount, setMeal, new ArrayList<>(items), isAddon);
        orders.add(order);
        return order;
    }

    public synchronized void removeOrder(String orderId) {
        if (orderId == null) return;
        orders.removeIf(order -> orderId.equalsIgnoreCase(order.getOrderId()));
    }

    public synchronized BacksiteOrder createOrderWithId(String orderId, String tableCode, int headCount, String setMeal, List<String> items, boolean isAddon) {
        BacksiteOrder existing = findById(orderId);
        if (existing != null) {
            return existing;
        }
        BacksiteOrder order = new BacksiteOrder(orderId, tableCode, headCount, setMeal, new ArrayList<>(items), isAddon);
        orders.add(order);
        return order;
    }

    public boolean hasInitialOrder(String tableCode) {
        if (tableCode == null) return false;
        for (BacksiteOrder order : orders) {
            if (tableCode.equalsIgnoreCase(order.getTableCode()) && !order.isAddon() && !order.isCheckedOut()) {
                return true;
            }
        }
        return false;
    }

    public BacksiteOrder getActiveInitialOrder(String tableCode) {
        if (tableCode == null) return null;
        for (BacksiteOrder order : orders) {
            if (tableCode.equalsIgnoreCase(order.getTableCode()) && !order.isAddon() && !order.isCheckedOut()) {
                return order;
            }
        }
        return null;
    }

    public List<BacksiteOrder> getAllOrders() {
        return Collections.unmodifiableList(orders);
    }

    public List<BacksiteOrder> getPreparingOrders() {
        List<BacksiteOrder> result = new ArrayList<>();
        for (BacksiteOrder order : orders) {
            if (order.isPreparing()) {
                result.add(order);
            }
        }
        return result;
    }

    public List<BacksiteOrder> getReadyOrders() {
        List<BacksiteOrder> result = new ArrayList<>();
        for (BacksiteOrder order : orders) {
            if (order.isReadyForPickup()) {
                result.add(order);
            }
        }
        return result;
    }

    public List<BacksiteOrder> getServedOrders() {
        List<BacksiteOrder> result = new ArrayList<>();
        for (BacksiteOrder order : orders) {
            if (order.isServed()) {
                result.add(order);
            }
        }
        return result;
    }

    public BacksiteOrder findById(String orderId) {
        if (orderId == null) return null;
        for (BacksiteOrder order : orders) {
            if (orderId.equalsIgnoreCase(order.getOrderId())) {
                return order;
            }
        }
        return null;
    }

    public boolean hasInitialOrderBeenDelivered(String tableCode) {
        if (tableCode == null) return false;
        for (BacksiteOrder order : orders) {
            if (tableCode.equalsIgnoreCase(order.getTableCode()) && !order.isAddon() && order.isServed()) {
                return true;
            }
        }
        return false;
    }

    public synchronized void checkoutTable(String tableCode) {
        if (tableCode == null) return;
        for (BacksiteOrder order : orders) {
            if (tableCode.equalsIgnoreCase(order.getTableCode())) {
                order.markCheckedOut();
            }
        }
    }

    public boolean isTableCheckedOut(String tableCode) {
        if (tableCode == null) return false;
        boolean hasOrders = false;
        for (BacksiteOrder order : orders) {
            if (tableCode.equalsIgnoreCase(order.getTableCode())) {
                hasOrders = true;
                if (!order.isCheckedOut()) {
                    return false;
                }
            }
        }
        return hasOrders;
    }

    /**
     * Clears all orders and resets order numbering completely clean.
     */
    public synchronized void clearAll() {
        orders.clear();
        orderCounter.set(1);
    }
}
