package com.example.samgyupsalorderingsystem;

import java.util.List;

public class BacksiteOrder {

    public enum Status {
        PREPARING("Preparing in Kitchen"),
        READY_FOR_PICKUP("Ready for Pickup 🔔"),
        SERVED("Served to Customer ✓"),
        CHECKED_OUT("Officially Checked Out 🏁");

        private final String displayLabel;

        Status(String displayLabel) {
            this.displayLabel = displayLabel;
        }

        public String getDisplayLabel() {
            return displayLabel;
        }
    }

    private final String orderId;
    private final String tableCode;
    private final int headCount;
    private final String setMeal;
    private final List<String> items;
    private final boolean isAddon;
    private Status status;

    public BacksiteOrder(String orderId, String tableCode, String setMeal, List<String> items) {
        this(orderId, tableCode, 2, setMeal, items, false);
    }

    public BacksiteOrder(String orderId, String tableCode, String setMeal, List<String> items, boolean isAddon) {
        this(orderId, tableCode, 2, setMeal, items, isAddon);
    }

    public BacksiteOrder(String orderId, String tableCode, int headCount, String setMeal, List<String> items, boolean isAddon) {
        this.orderId = orderId;
        this.tableCode = tableCode;
        this.headCount = Math.max(1, headCount);
        this.setMeal = setMeal;
        this.items = items;
        this.isAddon = isAddon;
        this.status = Status.PREPARING;
    }

    public int getHeadCount() {
        return headCount;
    }

    public String getTableDisplay() {
        return tableCode + " (" + headCount + " Pax)";
    }

    public boolean isAddon() {
        return isAddon;
    }

    public String getOrderTypeLabel() {
        return isAddon ? "ADD-ON" : "INITIAL ORDER";
    }

    public String getOrderId() {
        return orderId;
    }

    public String getTableCode() {
        return tableCode;
    }

    public String getSetMeal() {
        return setMeal;
    }

    public List<String> getItems() {
        return items;
    }

    public Status getStatus() {
        return status;
    }

    public void markReady() {
        this.status = Status.READY_FOR_PICKUP;
    }

    public void markServed() {
        this.status = Status.SERVED;
    }

    public void markCheckedOut() {
        this.status = Status.CHECKED_OUT;
    }

    public boolean isPreparing() {
        return status == Status.PREPARING;
    }

    public boolean isReadyForPickup() {
        return status == Status.READY_FOR_PICKUP;
    }

    public boolean isServed() {
        return status == Status.SERVED;
    }

    public boolean isCheckedOut() {
        return status == Status.CHECKED_OUT;
    }

    public String getFormattedItems() {
        StringBuilder sb = new StringBuilder();
        for (String item : items) {
            if (sb.length() > 0) sb.append("\n");
            sb.append("• ").append(item);
        }
        return sb.toString();
    }
}
