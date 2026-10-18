package com.example.samgyupsalorderingsystem;

import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class ServiceRequestManagerTest {

    private ServiceRequestManager manager;

    @Before
    public void setUp() {
        manager = ServiceRequestManager.getInstance();
        manager.clearAll();
    }

    @Test
    public void testAssistanceRequestLifecycle() {
        assertTrue(manager.getActiveRequests().isEmpty());

        // Step 1: Customer requests assistance
        ServiceRequestManager.Request req1 = manager.requestAssistance("Table 3", "Staff Assistance / Help Needed");
        assertNotNull(req1);
        assertEquals("Table 3", req1.getTableCode());
        assertEquals("Staff Assistance / Help Needed", req1.getMessage());
        assertEquals("REQ-1", req1.getId());
        assertEquals(1, manager.getActiveRequests().size());

        // Step 2: Another table requests assistance
        ServiceRequestManager.Request req2 = manager.requestAssistance("Table 1", "Staff Assistance / Help Needed");
        assertNotNull(req2);
        assertEquals("REQ-2", req2.getId());
        assertEquals(2, manager.getActiveRequests().size());

        // Step 3: Floor staff attends to Table 3 grill change
        manager.dismissRequest(req1.getId());
        List<ServiceRequestManager.Request> remaining = manager.getActiveRequests();
        assertEquals(1, remaining.size());
        assertEquals("REQ-2", remaining.get(0).getId());

        // Step 4: Clear all resets state
        manager.clearAll();
        assertTrue(manager.getActiveRequests().isEmpty());
    }
}
