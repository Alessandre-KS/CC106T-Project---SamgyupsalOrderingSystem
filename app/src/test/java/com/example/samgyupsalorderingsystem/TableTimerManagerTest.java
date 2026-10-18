package com.example.samgyupsalorderingsystem;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class TableTimerManagerTest {

    private TableTimerManager timerManager;

    @Before
    public void setUp() {
        timerManager = TableTimerManager.getInstance();
        timerManager.resetAll();
    }

    @Test
    public void testTimerLifecycle() {
        String table = "Table 1";

        // Step 1: Before staff delivery, timer has NOT started
        assertFalse(timerManager.isTimerStarted(table));
        assertEquals("60:00", timerManager.getFormattedRemainingTime(table));
        assertFalse(timerManager.isExpired(table));

        // Step 2: Staff delivers first food order -> officially starts 1-hour timer
        boolean started = timerManager.startTimer(table);
        assertTrue(started);
        assertTrue(timerManager.isTimerStarted(table));

        // Remaining time should be at most 1 hour (3600000 ms) and positive
        long remaining = timerManager.getRemainingMillis(table);
        assertTrue(remaining > 0 && remaining <= TableTimerManager.ONE_HOUR_MILLIS);
        assertFalse(timerManager.isExpired(table));

        // Formatted time should be in mm:ss format and roughly 59:59 or 60:00
        String formatted = timerManager.getFormattedRemainingTime(table);
        assertTrue(formatted.startsWith("59:") || formatted.equals("60:00"));

        // Step 3: Subsequent delivery/orders do NOT reset or extend the timer
        boolean secondCall = timerManager.startTimer(table);
        assertFalse("Subsequent calls to startTimer must not restart the timer", secondCall);
    }

    @Test
    public void testTableCodeNormalization() {
        // "table 2" vs "Table 2"
        assertTrue(timerManager.startTimer("table 2"));
        assertTrue(timerManager.isTimerStarted("Table 2"));
        assertTrue(timerManager.isTimerStarted("table 2"));
    }

    @Test
    public void testResetTable() {
        timerManager.startTimer("Table 3");
        assertTrue(timerManager.isTimerStarted("Table 3"));

        timerManager.resetTable("Table 3");
        assertFalse(timerManager.isTimerStarted("Table 3"));
        assertEquals("60:00", timerManager.getFormattedRemainingTime("Table 3"));
    }

    @Test
    public void testExtendTimeActive() {
        String table = "Table 4";
        timerManager.startTimer(table);
        long initialRemaining = timerManager.getRemainingMillis(table);

        // Extend by 30 minutes
        long thirtyMins = 30 * 60 * 1000L;
        timerManager.extendTime(table, thirtyMins);

        long newRemaining = timerManager.getRemainingMillis(table);
        assertTrue("Remaining time should increase by ~30 mins", newRemaining >= initialRemaining + thirtyMins - 1000);
        String formatted = timerManager.getFormattedRemainingTime(table);
        assertTrue(formatted.startsWith("89:") || formatted.startsWith("90:"));
    }

    @Test
    public void testExtendTimePending() {
        String table = "Table 5";
        // Extend before staff starts the timer
        timerManager.extendTime(table, 30 * 60 * 1000L);
        assertEquals("90:00", timerManager.getFormattedRemainingTime(table));

        // When staff starts timer, pending time is applied
        timerManager.startTimer(table);
        assertTrue(timerManager.isTimerStarted(table));
        long remaining = timerManager.getRemainingMillis(table);
        assertTrue(remaining > TableTimerManager.ONE_HOUR_MILLIS);
    }

    @Test
    public void testTableCheckout() {
        String table = "Table 1";
        timerManager.startTimer(table);
        assertTrue(timerManager.isTimerStarted(table));
        assertFalse(timerManager.isTableCheckedOut(table));

        // Customer officially checks out
        timerManager.checkoutTable(table);
        assertFalse(timerManager.isTimerStarted(table));
        assertTrue(timerManager.isTableCheckedOut(table));

        // New customer arrives at Table 1
        timerManager.startTimer(table);
        assertTrue(timerManager.isTimerStarted(table));
        assertFalse(timerManager.isTableCheckedOut(table));
    }

    @Test
    public void testExtensionFeeTrackingAndCheckout() {
        String table = "Table 2";
        assertEquals(0, timerManager.getExtensionFee(table));

        // +15 mins (+₱100)
        timerManager.extendTime(table, 15 * 60 * 1000L, 100);
        assertEquals(100, timerManager.getExtensionFee(table));

        // +30 mins (+₱180) additional
        timerManager.extendTime(table, 30 * 60 * 1000L, 180);
        assertEquals(280, timerManager.getExtensionFee(table));

        // Checkout clears fee
        timerManager.checkoutTable(table);
        assertEquals(0, timerManager.getExtensionFee(table));
    }

    @Test
    public void testExtensionFeeReset() {
        String table = "Table 3";
        timerManager.extendTime(table, 60 * 60 * 1000L, 300);
        assertEquals(300, timerManager.getExtensionFee(table));

        timerManager.resetTable(table);
        assertEquals(0, timerManager.getExtensionFee(table));
    }
}
