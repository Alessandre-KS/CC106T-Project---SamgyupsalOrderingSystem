package com.example.samgyupsalorderingsystem;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AdminActivity extends AppCompatActivity {

    private TextView tvAdminSales;
    private TextView tvAdminActiveTables;
    private TextView tvAdminCompletedOrders;

    private Handler pollHandler;
    private Runnable pollRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_admin);

        View adminRoot = findViewById(R.id.adminRoot);
        View headerContent = findViewById(R.id.adminHeaderContent);
        View scrollView = findViewById(R.id.adminScrollView);

        int baseHeaderTopPadding = headerContent.getPaddingTop();
        int baseBottomPadding = scrollView.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(adminRoot, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, 0, systemBars.right, 0);
            headerContent.setPadding(
                    headerContent.getPaddingLeft(),
                    baseHeaderTopPadding + systemBars.top,
                    headerContent.getPaddingRight(),
                    headerContent.getPaddingBottom()
            );
            scrollView.setPadding(
                    scrollView.getPaddingLeft(),
                    scrollView.getPaddingTop(),
                    scrollView.getPaddingRight(),
                    baseBottomPadding + systemBars.bottom
            );
            return insets;
        });

        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        if (controller != null) {
            controller.setAppearanceLightStatusBars(false);
            controller.setAppearanceLightNavigationBars(true);
        }

        initViews();
        updateMetrics();
    }

    @Override
    protected void onResume() {
        super.onResume();
        syncAllData();
        startPolling();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopPolling();
    }

    private void syncAllData() {
        BackendSyncManager.getInstance().syncPricing(() -> {
            BackendSyncManager.getInstance().syncAuth(() -> {
                BackendSyncManager.getInstance().syncOrders(() -> {
                    BackendSyncManager.getInstance().syncAllTimers(this::updateMetrics);
                });
            });
        });
    }

    private void startPolling() {
        if (pollHandler == null) pollHandler = new Handler(Looper.getMainLooper());
        if (pollRunnable == null) {
            pollRunnable = new Runnable() {
                @Override
                public void run() {
                    BackendSyncManager.getInstance().syncOrders(() -> {
                        BackendSyncManager.getInstance().syncAllTimers(AdminActivity.this::updateMetrics);
                    });
                    if (pollHandler != null) {
                        pollHandler.postDelayed(this, 3000);
                    }
                }
            };
        }
        pollHandler.postDelayed(pollRunnable, 3000);
    }

    private void stopPolling() {
        if (pollHandler != null && pollRunnable != null) {
            pollHandler.removeCallbacks(pollRunnable);
        }
    }

    private void initViews() {
        tvAdminSales = findViewById(R.id.tvAdminSales);
        tvAdminActiveTables = findViewById(R.id.tvAdminActiveTables);
        tvAdminCompletedOrders = findViewById(R.id.tvAdminCompletedOrders);

        MaterialButton btnAdminLogout = findViewById(R.id.btnAdminLogout);
        MaterialButton btnActiveTables = findViewById(R.id.btnActiveTables);
        MaterialButton btnManageMenu = findViewById(R.id.btnManageMenu);
        MaterialButton btnManageStaff = findViewById(R.id.btnManageStaff);
        MaterialButton btnReports = findViewById(R.id.btnReports);
        MaterialButton btnAdminResetDb = findViewById(R.id.btnAdminResetDb);

        btnActiveTables.setOnClickListener(v -> showActiveTablesDialog());
        btnManageMenu.setOnClickListener(v -> showMenuPricingDialog());
        btnManageStaff.setOnClickListener(v -> showStaffAccountsDialog());
        btnReports.setOnClickListener(v -> showSalesReportDialog());
        btnAdminResetDb.setOnClickListener(v -> showResetDatabaseDialog());

        btnAdminLogout.setOnClickListener(v -> {
            Toast.makeText(this, "Admin signed out", Toast.LENGTH_SHORT).show();
            finish();
        });

        TextView tvAdminAppVersion = findViewById(R.id.tvAdminAppVersion);
        if (tvAdminAppVersion != null) {
            try {
                String v = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
                tvAdminAppVersion.setText("Samgyupsal Ordering System • v" + v);
            } catch (Exception ignored) {
                tvAdminAppVersion.setText("Samgyupsal Ordering System • v1.0.6");
            }
        }
    }

    private void updateMetrics() {
        List<BacksiteOrder> orders = OrderRepository.getInstance().getAllOrders();
        Set<String> activeTables = new HashSet<>();
        int completedOrders = 0;
        int totalSales = 0;

        // Calculate sales from distinct table initial orders
        Set<String> billedTables = new HashSet<>();
        for (BacksiteOrder order : orders) {
            if (!TableTimerManager.getInstance().isTableCheckedOut(order.getTableCode()) && !order.isCheckedOut()) {
                activeTables.add(order.getTableCode());
            }
            if (order.isServed() || order.isCheckedOut()) {
                completedOrders++;
            }
            if (!order.isAddon() && !billedTables.contains(order.getTableCode())) {
                billedTables.add(order.getTableCode());
                SetMeal set = SetMeal.fromString(order.getSetMeal());
                int pricePerPax = set.getPricePerPax();
                int extFee = TableTimerManager.getInstance().getExtensionFee(order.getTableCode());
                totalSales += (order.getHeadCount() * pricePerPax) + extFee;
            }
        }

        if (tvAdminSales != null) {
            tvAdminSales.setText("₱" + String.format("%,d", totalSales));
        }
        if (tvAdminActiveTables != null) {
            tvAdminActiveTables.setText(activeTables.size() + " Active");
        }
        if (tvAdminCompletedOrders != null) {
            tvAdminCompletedOrders.setText(String.valueOf(completedOrders));
        }
    }

    private void showActiveTablesDialog() {
        List<BacksiteOrder> orders = OrderRepository.getInstance().getAllOrders();
        Set<String> tables = new HashSet<>();
        for (BacksiteOrder o : orders) {
            if (!TableTimerManager.getInstance().isTableCheckedOut(o.getTableCode()) && !o.isCheckedOut()) {
                tables.add(o.getTableCode());
            }
        }

        if (tables.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("Active Table Sessions")
                    .setMessage("No active dining sessions.\nAll tables are currently available.")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }

        StringBuilder sb = new StringBuilder();
        for (String table : tables) {
            boolean started = TableTimerManager.getInstance().isTimerStarted(table);
            String time = TableTimerManager.getInstance().getFormattedRemainingTime(table);
            int extFee = TableTimerManager.getInstance().getExtensionFee(table);
            BacksiteOrder initOrder = OrderRepository.getInstance().getActiveInitialOrder(table);
            String plan = initOrder != null ? " (" + initOrder.getHeadCount() + " Pax • " + initOrder.getSetMeal() + ")" : "";

            sb.append("• ").append(table).append(plan).append(":\n  ")
              .append(started ? "⏱️ " + time + " remaining" : "⏳ Waiting for food delivery");
            if (extFee > 0) {
                sb.append(" (+₱").append(extFee).append(" extension)");
            }
            sb.append("\n\n");
        }

        new AlertDialog.Builder(this)
                .setTitle("Active Table Timers (" + tables.size() + " Active)")
                .setMessage(sb.toString().trim())
                .setPositiveButton("OK", null)
                .setNeutralButton("Checkout a Table", (d, w) -> {
                    String[] tableArr = tables.toArray(new String[0]);
                    new AlertDialog.Builder(this)
                            .setTitle("Select Table to Checkout")
                            .setItems(tableArr, (dialog, idx) -> {
                                String selected = tableArr[idx];
                                OrderRepository.getInstance().checkoutTable(selected);
                                TableTimerManager.getInstance().checkoutTable(selected);
                                BackendSyncManager.getInstance().checkoutTableOnBackend(selected, this::updateMetrics);
                                Toast.makeText(this, selected + " checked out by Admin!", Toast.LENGTH_SHORT).show();
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                })
                .show();
    }

    private void showMenuPricingDialog() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_manage_pricing, null);
        TextInputEditText etA = view.findViewById(R.id.etPriceA);
        TextInputEditText etB = view.findViewById(R.id.etPriceB);
        TextInputEditText etC = view.findViewById(R.id.etPriceC);

        etA.setText(String.valueOf(SetMeal.getPriceSetA()));
        etB.setText(String.valueOf(SetMeal.getPriceSetB()));
        etC.setText(String.valueOf(SetMeal.getPriceSetC()));

        new AlertDialog.Builder(this)
                .setTitle("Manage Menu Pricing")
                .setView(view)
                .setPositiveButton("Save & Update Prices", (d, w) -> {
                    try {
                        int priceA = Integer.parseInt(etA.getText() != null ? etA.getText().toString().trim() : "0");
                        int priceB = Integer.parseInt(etB.getText() != null ? etB.getText().toString().trim() : "0");
                        int priceC = Integer.parseInt(etC.getText() != null ? etC.getText().toString().trim() : "0");
                        if (priceA > 0 && priceB > 0 && priceC > 0) {
                            BackendSyncManager.getInstance().updatePricing(priceA, priceB, priceC, this::updateMetrics);
                            Toast.makeText(this, "Prices updated! Set A: ₱" + priceA + ", Set B: ₱" + priceB + ", Set C: ₱" + priceC, Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(this, "Prices must be greater than zero", Toast.LENGTH_SHORT).show();
                        }
                    } catch (Exception e) {
                        Toast.makeText(this, "Please enter valid numeric prices", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showStaffAccountsDialog() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_manage_staff, null);
        TextInputEditText etStaff = view.findViewById(R.id.etStaffPass);
        TextInputEditText etAdmin = view.findViewById(R.id.etAdminPass);

        etStaff.setText(AuthManager.getStaffPasskey());
        etAdmin.setText(AuthManager.getAdminPasskey());

        new AlertDialog.Builder(this)
                .setTitle("Staff Passkeys & Accounts")
                .setView(view)
                .setPositiveButton("Save Passkeys", (d, w) -> {
                    String sPass = etStaff.getText() != null ? etStaff.getText().toString().trim() : "";
                    String aPass = etAdmin.getText() != null ? etAdmin.getText().toString().trim() : "";
                    if (!sPass.isEmpty() && !aPass.isEmpty()) {
                        BackendSyncManager.getInstance().updateAuth(sPass, aPass, null);
                        Toast.makeText(this, "Passkeys saved successfully!", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Passkeys cannot be empty", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showSalesReportDialog() {
        List<BacksiteOrder> orders = OrderRepository.getInstance().getAllOrders();
        if (orders.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("Daily Sales & Consumption Report")
                    .setMessage("No sales recorded yet today.\nTotal Revenue: ₱0\nNo items ordered.")
                    .setPositiveButton("Close", null)
                    .show();
            return;
        }

        StringBuilder sb = new StringBuilder();
        int grandTotal = 0;
        int totalPax = 0;
        Set<String> billedTables = new HashSet<>();
        Map<String, Integer> itemConsumption = new LinkedHashMap<>();

        // 1. Aggregate Food Item Consumptions across all orders (initial + add-ons)
        for (BacksiteOrder order : orders) {
            if (order.getItems() != null) {
                for (String itemStr : order.getItems()) {
                    if (itemStr == null || itemStr.trim().isEmpty()) continue;
                    int qty = 1;
                    String name = itemStr.trim();
                    if (name.contains("x ")) {
                        String[] parts = name.split("x ", 2);
                        try {
                            qty = Integer.parseInt(parts[0].trim());
                            name = parts[1].trim();
                        } catch (NumberFormatException ignored) {}
                    }
                    itemConsumption.put(name, itemConsumption.getOrDefault(name, 0) + qty);
                }
            }
        }

        // 2. Aggregate Financial Billing per Table
        for (BacksiteOrder order : orders) {
            if (!order.isAddon() && !billedTables.contains(order.getTableCode())) {
                billedTables.add(order.getTableCode());
                SetMeal set = SetMeal.fromString(order.getSetMeal());
                int price = set.getPricePerPax();
                int extFee = TableTimerManager.getInstance().getExtensionFee(order.getTableCode());
                int subtotal = (order.getHeadCount() * price) + extFee;
                grandTotal += subtotal;
                totalPax += order.getHeadCount();

                sb.append("• ").append(order.getTableCode())
                  .append(" (").append(order.getHeadCount()).append(" Pax • ").append(order.getSetMeal()).append(")\n")
                  .append("  Base: ₱").append(price).append(" × ").append(order.getHeadCount()).append(" = ₱").append(String.format("%,d", order.getHeadCount() * price));
                if (extFee > 0) {
                    sb.append(" (+₱").append(extFee).append(" ext)");
                }
                sb.append("\n  Subtotal: ₱").append(String.format("%,d", subtotal)).append("\n\n");
            }
        }

        sb.append("=============================\n");
        sb.append("📊 FINANCIAL OVERVIEW:\n");
        sb.append("• Total Tables Billed: ").append(billedTables.size()).append("\n");
        sb.append("• Total Guests (Pax): ").append(totalPax).append("\n");
        sb.append("• Total Gross Revenue: ₱").append(String.format("%,d", grandTotal)).append("\n");

        if (!itemConsumption.isEmpty()) {
            sb.append("\n=============================\n");
            sb.append("🥩 DAILY FOOD CONSUMPTION:\n");
            List<Map.Entry<String, Integer>> sorted = new ArrayList<>(itemConsumption.entrySet());
            sorted.sort((a, b) -> b.getValue().compareTo(a.getValue()));
            for (Map.Entry<String, Integer> entry : sorted) {
                sb.append("• ").append(entry.getKey()).append(": ").append(entry.getValue()).append(" ordered\n");
            }
        }

        new AlertDialog.Builder(this)
                .setTitle("Daily Sales & Consumption Report")
                .setMessage(sb.toString().trim())
                .setPositiveButton("Close", null)
                .show();
    }

    private void showResetDatabaseDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Reset Entire Restaurant Data")
                .setMessage("This will clear all orders, reset table dining sessions, and wipe the database clean (0 customers / ₱0 sales).\n\nProceed?")
                .setPositiveButton("Reset Everything", (dialog, which) -> {
                    OrderRepository.getInstance().clearAll();
                    TableTimerManager.getInstance().resetAll();
                    ServiceRequestManager.getInstance().clearAll();
                    BackendSyncManager.getInstance().resetBackend(null);
                    updateMetrics();
                    Toast.makeText(this, "Restaurant data reset! 0 orders, ₱0 sales.", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
