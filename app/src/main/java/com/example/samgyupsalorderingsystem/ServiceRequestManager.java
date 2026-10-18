package com.example.samgyupsalorderingsystem;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages customer service bell requests (FR-009: Call Server / Assistance).
 */
public class ServiceRequestManager {

    public static class Request {
        private final String id;
        private final String tableCode;
        private final String message;
        private final boolean isCheckout;
        private final int totalBill;
        private final long timestamp;

        public Request(String id, String tableCode, String message, long timestamp) {
            this(id, tableCode, message, false, 0, timestamp);
        }

        public Request(String id, String tableCode, String message, boolean isCheckout, int totalBill, long timestamp) {
            this.id = id;
            this.tableCode = tableCode;
            this.message = message;
            this.isCheckout = isCheckout;
            this.totalBill = totalBill;
            this.timestamp = timestamp;
        }

        public String getId() { return id; }
        public String getTableCode() { return tableCode; }
        public String getMessage() { return message; }
        public boolean isCheckout() { return isCheckout; }
        public int getTotalBill() { return totalBill; }
        public long getTimestamp() { return timestamp; }
    }

    private static ServiceRequestManager instance;
    private final List<Request> requests = new CopyOnWriteArrayList<>();
    private final AtomicInteger counter = new AtomicInteger(1);

    private ServiceRequestManager() {}

    public static synchronized ServiceRequestManager getInstance() {
        if (instance == null) {
            instance = new ServiceRequestManager();
        }
        return instance;
    }

    public synchronized Request requestAssistance(String tableCode, String message) {
        String id = "REQ-" + counter.getAndIncrement();
        Request req = new Request(id, tableCode, message, false, 0, System.currentTimeMillis());
        requests.add(req);
        return req;
    }

    public synchronized Request requestCheckout(String tableCode, int totalBill) {
        String id = "REQ-" + counter.getAndIncrement();
        Request req = new Request(id, tableCode, "Table requesting checkout", true, totalBill, System.currentTimeMillis());
        requests.add(req);
        return req;
    }

    public synchronized void setRequests(List<Request> newRequests) {
        requests.clear();
        if (newRequests != null) {
            requests.addAll(newRequests);
        }
    }

    public List<Request> getActiveRequests() {
        return requests;
    }

    public synchronized void dismissRequest(String id) {
        if (id == null) return;
        requests.removeIf(r -> id.equals(r.getId()));
    }

    public synchronized void clearAll() {
        requests.clear();
        counter.set(1);
    }
}
