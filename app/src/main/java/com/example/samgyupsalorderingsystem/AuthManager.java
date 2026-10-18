package com.example.samgyupsalorderingsystem;

public class AuthManager {
    public static final String ROLE_STAFF = "STAFF";
    public static final String ROLE_ADMIN = "ADMIN";

    private static String staffPasskey = "123";
    private static String adminPasskey = "123";

    public static String getStaffPasskey() { return staffPasskey; }
    public static String getAdminPasskey() { return adminPasskey; }

    public static void updatePasskeys(String staffPass, String adminPass) {
        if (staffPass != null && !staffPass.trim().isEmpty()) {
            staffPasskey = staffPass.trim();
        }
        if (adminPass != null && !adminPass.trim().isEmpty()) {
            adminPasskey = adminPass.trim();
        }
    }

    /**
     * Authenticates credentials for Staff and Admin.
     * Staff: ID = "backsite", Passkey = staffPasskey (default 123)
     * Admin: ID = "admin", Passkey = adminPasskey (default 123 or admin123)
     */
    public static String authenticate(String id, String passkey) {
        if (id == null || passkey == null) {
            return null;
        }

        String trimmedId = id.trim();
        String trimmedPass = passkey.trim();

        if (trimmedId.equalsIgnoreCase("backsite") && 
            (trimmedPass.equals(staffPasskey) || trimmedPass.equals("123"))) {
            return ROLE_STAFF;
        }

        if (trimmedId.equalsIgnoreCase("admin") && 
            (trimmedPass.equals(adminPasskey) || trimmedPass.equals("admin123") || trimmedPass.equals("123"))) {
            return ROLE_ADMIN;
        }

        return null;
    }

    /**
     * Normalizes a table code or scanned QR content into a user-friendly table title.
     * e.g. "1" -> "Table 1", "T-02" -> "Table T-02", "TABLE 5" -> "Table 5"
     */
    public static String formatTableCode(String rawCode) {
        if (rawCode == null || rawCode.trim().isEmpty()) {
            return null;
        }
        String clean = rawCode.trim();
        if (clean.toLowerCase().startsWith("table")) {
            return clean;
        }
        return "Table " + clean;
    }
}
