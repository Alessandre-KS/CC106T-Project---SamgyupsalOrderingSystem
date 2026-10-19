package com.example.samgyupsalorderingsystem;

import android.os.Bundle;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AdminActivity extends AppCompatActivity {

    private TextView tvAdminSales;
    private TextView tvAdminActiveTables;
    private TextView tvAdminCompletedOrders;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin);

        initViews();
        updateMetrics();
    }

    @Override
    protected void onResume() {
        super.onResume();
        BackendSyncManager.getInstance().syncPricing(() -> {
            BackendSyncManager.getInstance().syncAuth(() -> {
                BackendSyncManager.getInstance().syncOrders(this::updateMetrics);
            });
        });
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
                tvAdminAppVersion.setText("Samgyupsal Ordering System • v1.0.2");
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
            tvAdminActiveTables.setText(activeTables.size() + " / 12");
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
            sb.append("• ").append(table).append(": ")
              .append(started ? "⏱️ " + time + " remaining" : "⏳ Waiting for food delivery");
            if (extFee > 0) {
                sb.append(" (+₱").append(extFee).append(" extension fee)");
            }
            sb.append("\n");
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
        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        int pad = (int) (18 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad, pad, pad);

        TextView tvHeader = new TextView(this);
        tvHeader.setText("Update dining set prices per person (pax).\nChanges apply dynamically across all customer tables.");
        tvHeader.setTextSize(13);
        tvHeader.setTextColor(getColor(R.color.text_muted));
        tvHeader.setPadding(0, 0, 0, (int) (12 * getResources().getDisplayMetrics().density));
        layout.addView(tvHeader);

        TextView tvA = new TextView(this);
        tvA.setText("SET MEAL A (Typical Pork) - ₱ / pax:");
        tvA.setTypeface(null, android.graphics.Typeface.BOLD);
        layout.addView(tvA);

        EditText etA = new EditText(this);
        etA.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        etA.setText(String.valueOf(SetMeal.getPriceSetA()));
        layout.addView(etA);

        TextView tvB = new TextView(this);
        tvB.setText("SET MEAL B (Pork + Cheese Dip) - ₱ / pax:");
        tvB.setTypeface(null, android.graphics.Typeface.BOLD);
        tvB.setPadding(0, (int) (8 * getResources().getDisplayMetrics().density), 0, 0);
        layout.addView(tvB);

        EditText etB = new EditText(this);
        etB.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        etB.setText(String.valueOf(SetMeal.getPriceSetB()));
        layout.addView(etB);

        TextView tvC = new TextView(this);
        tvC.setText("SET MEAL C (Pork & Beef Unlimited) - ₱ / pax:");
        tvC.setTypeface(null, android.graphics.Typeface.BOLD);
        tvC.setPadding(0, (int) (8 * getResources().getDisplayMetrics().density), 0, 0);
        layout.addView(tvC);

        EditText etC = new EditText(this);
        etC.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        etC.setText(String.valueOf(SetMeal.getPriceSetC()));
        layout.addView(etC);

        new AlertDialog.Builder(this)
                .setTitle("🥩 Manage Menu Pricing")
                .setView(layout)
                .setPositiveButton("Save & Update Prices", (d, w) -> {
                    try {
                        int priceA = Integer.parseInt(etA.getText().toString().trim());
                        int priceB = Integer.parseInt(etB.getText().toString().trim());
                        int priceC = Integer.parseInt(etC.getText().toString().trim());
                        if (priceA > 0 && priceB > 0 && priceC > 0) {
                            BackendSyncManager.getInstance().updatePricing(priceA, priceB, priceC, this::updateMetrics);
                            Toast.makeText(this, "Prices updated! Set A: ₱" + priceA + ", Set B: ₱" + priceB + ", Set C: ₱" + priceC, Toast.LENGTH_LONG).show();
                        }
                    } catch (Exception e) {
                        Toast.makeText(this, "Please enter valid numeric prices", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showStaffAccountsDialog() {
        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        int pad = (int) (18 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad, pad, pad);

        TextView tvInfo = new TextView(this);
        tvInfo.setText("Current accounts:\n• Staff ID: backsite\n• Admin ID: admin\n\nUpdate login passkeys below:");
        tvInfo.setTextSize(13);
        tvInfo.setTextColor(getColor(R.color.text_muted));
        layout.addView(tvInfo);

        TextView tvStaff = new TextView(this);
        tvStaff.setText("Floor / Kitchen Staff Passkey:");
        tvStaff.setTypeface(null, android.graphics.Typeface.BOLD);
        tvStaff.setPadding(0, (int) (12 * getResources().getDisplayMetrics().density), 0, 0);
        layout.addView(tvStaff);

        EditText etStaff = new EditText(this);
        etStaff.setText(AuthManager.getStaffPasskey());
        layout.addView(etStaff);

        TextView tvAdmin = new TextView(this);
        tvAdmin.setText("Administrator Passkey:");
        tvAdmin.setTypeface(null, android.graphics.Typeface.BOLD);
        tvAdmin.setPadding(0, (int) (8 * getResources().getDisplayMetrics().density), 0, 0);
        layout.addView(tvAdmin);

        EditText etAdmin = new EditText(this);
        etAdmin.setText(AuthManager.getAdminPasskey());
        layout.addView(etAdmin);

        new AlertDialog.Builder(this)
                .setTitle("👥 Staff Passkeys & Accounts")
                .setView(layout)
                .setPositiveButton("Save Passkeys", (d, w) -> {
                    String sPass = etStaff.getText().toString().trim();
                    String aPass = etAdmin.getText().toString().trim();
                    if (!sPass.isEmpty() && !aPass.isEmpty()) {
                        BackendSyncManager.getInstance().updateAuth(sPass, aPass, null);
                        Toast.makeText(this, "Passkeys saved successfully!", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showSalesReportDialog() {
        List<BacksiteOrder> orders = OrderRepository.getInstance().getAllOrders();
        if (orders.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("Daily Sales Report")
                    .setMessage("No sales recorded yet today.\nTotal Revenue: ₱0")
                    .setPositiveButton("Close", null)
                    .show();
            return;
        }

        StringBuilder sb = new StringBuilder();
        int grandTotal = 0;
        int totalPax = 0;
        Set<String> billedTables = new HashSet<>();

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
                  .append("  Base Bill: ₱").append(price).append(" × ").append(order.getHeadCount()).append(" = ₱").append(String.format("%,d", order.getHeadCount() * price));
                if (extFee > 0) {
                    sb.append(" (+₱").append(extFee).append(" ext)");
                }
                sb.append("\n  Subtotal: ₱").append(String.format("%,d", subtotal)).append("\n\n");
            }
        }

        sb.append("-----------------------------\n");
        sb.append("TOTAL TABLES BILLED: ").append(billedTables.size()).append("\n");
        sb.append("TOTAL CUSTOMERS (PAX): ").append(totalPax).append("\n");
        sb.append("TOTAL REVENUE: ₱").append(String.format("%,d", grandTotal));

        new AlertDialog.Builder(this)
                .setTitle("Daily Sales Breakdown")
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
