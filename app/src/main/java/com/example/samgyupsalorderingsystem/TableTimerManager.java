package com.example.samgyupsalorderingsystem;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the 1-hour dining timer for customer tables.
 * Officially started by staff when the table's first food order is delivered.
 * Supports session extensions (+15 mins, +30 mins, +1 hour).
 */
public class TableTimerManager {

    public static final long ONE_HOUR_MILLIS = 60 * 60 * 1000L; // 1 hour (3,600,000 ms)

    private static TableTimerManager instance;
    private final Map<String, Long> tableExpiryTimes = new ConcurrentHashMap<>();
    private final Map<String, Long> pendingExtensionMillis = new ConcurrentHashMap<>();
    private final Map<String, Integer> tableExtensionFees = new ConcurrentHashMap<>();
    private final java.util.Set<String> checkedOutTables = ConcurrentHashMap.newKeySet();

    private TableTimerManager() {}

    public static synchronized TableTimerManager getInstance() {
        if (instance == null) {
            instance = new TableTimerManager();
        }
        return instance;
    }

    private String normalizeKey(String tableCode) {
        if (tableCode == null || tableCode.trim().isEmpty()) {
            return "";
        }
        String formatted = AuthManager.formatTableCode(tableCode);
        return (formatted != null ? formatted : tableCode).trim().toUpperCase(Locale.US);
    }

    /**
     * Staff starts the 1-hour timer when giving the first order of food to the customer.
     * Subsequent calls for the same table are ignored to prevent timer resets.
     */
    public synchronized boolean startTimer(String tableCode) {
        String key = normalizeKey(tableCode);
        if (key.isEmpty()) {
            return false;
        }
        if (!tableExpiryTimes.containsKey(key)) {
            checkedOutTables.remove(key);
            long pendingExtra = pendingExtensionMillis.getOrDefault(key, 0L);
            tableExpiryTimes.put(key, System.currentTimeMillis() + ONE_HOUR_MILLIS + pendingExtra);
            pendingExtensionMillis.remove(key);
            return true;
        }
        return false;
    }

    /**
     * Synchronizes table timer expiry with central backend authority.
     */
    public synchronized void syncTimer(String tableCode, long remainingMillis) {
        String key = normalizeKey(tableCode);
        if (key.isEmpty()) {
            return;
        }
        checkedOutTables.remove(key);
        tableExpiryTimes.put(key, System.currentTimeMillis() + remainingMillis);
        pendingExtensionMillis.remove(key);
    }

    public boolean isTimerStarted(String tableCode) {
        String key = normalizeKey(tableCode);
        if (key.isEmpty()) return false;
        return tableExpiryTimes.containsKey(key);
    }

    /**
     * Extends dining time for the given table.
     * If active: adds additionalMillis to remaining time.
     * If expired: grants additionalMillis starting from now.
     * If not yet started: queues additionalMillis to be added when food is delivered.
     */
    public synchronized void extendTime(String tableCode, long additionalMillis) {
        String key = normalizeKey(tableCode);
        if (key.isEmpty() || additionalMillis <= 0) {
            return;
        }
        if (!tableExpiryTimes.containsKey(key)) {
            long currentPending = pendingExtensionMillis.getOrDefault(key, 0L);
            pendingExtensionMillis.put(key, currentPending + additionalMillis);
        } else {
            long now = System.currentTimeMillis();
            long currentExpiry = tableExpiryTimes.get(key);
            long base = Math.max(now, currentExpiry);
            tableExpiryTimes.put(key, base + additionalMillis);
        }
    }

    /**
     * Extends dining time and accumulates the associated extension fee.
     */
    public synchronized void extendTime(String tableCode, long additionalMillis, int fee) {
        extendTime(tableCode, additionalMillis);
        if (fee > 0) {
            String key = normalizeKey(tableCode);
            if (!key.isEmpty()) {
                int currentFee = tableExtensionFees.getOrDefault(key, 0);
                tableExtensionFees.put(key, currentFee + fee);
            }
        }
    }

    public int getExtensionFee(String tableCode) {
        String key = normalizeKey(tableCode);
        if (key.isEmpty()) return 0;
        return tableExtensionFees.getOrDefault(key, 0);
    }

    public long getRemainingMillis(String tableCode) {
        String key = normalizeKey(tableCode);
        if (!tableExpiryTimes.containsKey(key)) {
            long pendingExtra = pendingExtensionMillis.getOrDefault(key, 0L);
            return ONE_HOUR_MILLIS + pendingExtra;
        }
        Long expiry = tableExpiryTimes.get(key);
        if (expiry == null) {
            return ONE_HOUR_MILLIS;
        }
        return Math.max(0, expiry - System.currentTimeMillis());
    }

    public boolean isExpired(String tableCode) {
        return isTimerStarted(tableCode) && getRemainingMillis(tableCode) <= 0;
    }

    public String getFormattedRemainingTime(String tableCode) {
        long remaining = getRemainingMillis(tableCode);
        long totalSeconds = remaining / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format(Locale.US, "%02d:%02d", minutes, seconds);
    }

    public synchronized void checkoutTable(String tableCode) {
        String key = normalizeKey(tableCode);
        if (!key.isEmpty()) {
            tableExpiryTimes.remove(key);
            pendingExtensionMillis.remove(key);
            tableExtensionFees.remove(key);
            checkedOutTables.add(key);
        }
    }

    public boolean isTableCheckedOut(String tableCode) {
        String key = normalizeKey(tableCode);
        return !key.isEmpty() && checkedOutTables.contains(key);
    }

    public synchronized void resetTable(String tableCode) {
        String key = normalizeKey(tableCode);
        if (!key.isEmpty()) {
            tableExpiryTimes.remove(key);
            pendingExtensionMillis.remove(key);
            tableExtensionFees.remove(key);
            checkedOutTables.remove(key);
        }
    }

    public synchronized void resetAll() {
        tableExpiryTimes.clear();
        pendingExtensionMillis.clear();
        tableExtensionFees.clear();
        checkedOutTables.clear();
    }
}
