package com.example.samgyupsalorderingsystem;

import org.junit.Test;
import static org.junit.Assert.*;

public class AuthManagerTest {

    @Test
    public void testStaffAuthenticationSuccess() {
        assertEquals(AuthManager.ROLE_STAFF, AuthManager.authenticate("backsite", "123"));
        assertEquals(AuthManager.ROLE_STAFF, AuthManager.authenticate("Backsite", "123"));
        assertEquals(AuthManager.ROLE_STAFF, AuthManager.authenticate("  backsite  ", "123"));
    }

    @Test
    public void testStaffAuthenticationFailure() {
        assertNull(AuthManager.authenticate("backsite", "wrongpass"));
        assertNull(AuthManager.authenticate("backsite", ""));
        assertNull(AuthManager.authenticate("unknown", "123"));
    }

    @Test
    public void testAdminAuthenticationSuccess() {
        assertEquals(AuthManager.ROLE_ADMIN, AuthManager.authenticate("admin", "admin123"));
        assertEquals(AuthManager.ROLE_ADMIN, AuthManager.authenticate("admin", "123"));
        assertEquals(AuthManager.ROLE_ADMIN, AuthManager.authenticate("ADMIN", "admin123"));
    }

    @Test
    public void testAdminAuthenticationFailure() {
        assertNull(AuthManager.authenticate("admin", "wrong"));
        assertNull(AuthManager.authenticate(null, null));
    }

    @Test
    public void testFormatTableCode() {
        assertEquals("Table 5", AuthManager.formatTableCode("5"));
        assertEquals("Table 1", AuthManager.formatTableCode("Table 1"));
        assertEquals("Table T-02", AuthManager.formatTableCode("T-02"));
        assertNull(AuthManager.formatTableCode(""));
        assertNull(AuthManager.formatTableCode("   "));
        assertNull(AuthManager.formatTableCode(null));
    }
}
