package com.example.samgyupsalorderingsystem;

import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class StaffActivity extends AppCompatActivity {

    private LinearLayout layoutChefView;
    private LinearLayout layoutFloorView;

    private TextView tvChefEmptyState;
    private TextView tvFloorEmptyState;
    private LinearLayout containerChefOrders;
    private LinearLayout containerFloorOrders;
    private LinearLayout containerAssistanceAlerts;
    private TextView tvNoActiveTimers;
    private LinearLayout containerTableTimers;

    private android.os.Handler pollHandler;
    private Runnable pollRunnable;
    private android.os.Handler timerTickerHandler;
    private Runnable timerTickerRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_staff);

        initViews();
        setupStationToggle();
        setupFloorExtendTime();
        setupFloorCheckout();
        refreshOrders();
    }

    @Override
    protected void onResume() {
        super.onResume();
        BackendSyncManager.getInstance().syncOrders(() -> {
            BackendSyncManager.getInstance().syncAllTimers(() -> {
                BackendSyncManager.getInstance().syncRequests(this::refreshOrders);
            });
        });
        startPolling();
        startTimerTicker();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopPolling();
        stopTimerTicker();
    }

    private void startPolling() {
        if (pollHandler == null) pollHandler = new android.os.Handler(android.os.Looper.getMainLooper());
        if (pollRunnable == null) {
            pollRunnable = new Runnable() {
                @Override
                public void run() {
                    BackendSyncManager.getInstance().syncOrders(() -> {
                        BackendSyncManager.getInstance().syncAllTimers(() -> {
                            BackendSyncManager.getInstance().syncRequests(() -> {
                                refreshOrders();
                            });
                        });
                    });
                    if (pollHandler != null) pollHandler.postDelayed(this, 2000);
                }
            };
        }
        pollHandler.postDelayed(pollRunnable, 2000);
    }

    private void stopPolling() {
        if (pollHandler != null && pollRunnable != null) {
            pollHandler.removeCallbacks(pollRunnable);
        }
    }

    private void startTimerTicker() {
        if (timerTickerHandler == null) timerTickerHandler = new android.os.Handler(android.os.Looper.getMainLooper());
        if (timerTickerRunnable == null) {
            timerTickerRunnable = new Runnable() {
                @Override
                public void run() {
                    populateTableTimers();
                    if (timerTickerHandler != null) timerTickerHandler.postDelayed(this, 1000);
                }
            };
        }
        timerTickerHandler.postDelayed(timerTickerRunnable, 1000);
    }

    private void stopTimerTicker() {
        if (timerTickerHandler != null && timerTickerRunnable != null) {
            timerTickerHandler.removeCallbacks(timerTickerRunnable);
        }
    }

    private void initViews() {
        layoutChefView = findViewById(R.id.layoutChefView);
        layoutFloorView = findViewById(R.id.layoutFloorView);

        tvChefEmptyState = findViewById(R.id.tvChefEmptyState);
        tvFloorEmptyState = findViewById(R.id.tvFloorEmptyState);
        containerChefOrders = findViewById(R.id.containerChefOrders);
        containerFloorOrders = findViewById(R.id.containerFloorOrders);
        containerAssistanceAlerts = findViewById(R.id.containerAssistanceAlerts);
        tvNoActiveTimers = findViewById(R.id.tvNoActiveTimers);
        containerTableTimers = findViewById(R.id.containerTableTimers);

        MaterialButton btnStaffLogout = findViewById(R.id.btnStaffLogout);
        btnStaffLogout.setOnClickListener(v -> {
            Toast.makeText(this, "Backsite signed out", Toast.LENGTH_SHORT).show();
            finish();
        });

        MaterialButton btnClearDatabase = findViewById(R.id.btnClearDatabase);
        if (btnClearDatabase != null) {
            btnClearDatabase.setOnClickListener(v -> showClearDatabaseConfirmation());
        }
    }

    private void setupStationToggle() {
        MaterialButtonToggleGroup toggleGroup = findViewById(R.id.toggleStationGroup);
        toggleGroup.check(R.id.btnTabChef);

        toggleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;

            if (checkedId == R.id.btnTabChef) {
                layoutChefView.setVisibility(View.VISIBLE);
                layoutFloorView.setVisibility(View.GONE);
            } else if (checkedId == R.id.btnTabFloor) {
                layoutChefView.setVisibility(View.GONE);
                layoutFloorView.setVisibility(View.VISIBLE);
            }
        });
    }

    private void setupFloorExtendTime() {
        MaterialButton btnStaffExtendTime = findViewById(R.id.btnStaffExtendTime);
        if (btnStaffExtendTime != null) {
            btnStaffExtendTime.setOnClickListener(v -> showStaffExtendTimeDialog());
        }
    }

    private void setupFloorCheckout() {
        MaterialButton btnStaffCheckout = findViewById(R.id.btnStaffCheckout);
        if (btnStaffCheckout != null) {
            btnStaffCheckout.setOnClickListener(v -> showStaffCheckoutDialog());
        }
    }

    /**
     * Refreshes both Chef and Floor Staff order queues from the live OrderRepository.
     */
    private void refreshOrders() {
        populateChefStation();
        populateFloorStation();
    }

    private void populateChefStation() {
        if (containerChefOrders == null) return;
        containerChefOrders.removeAllViews();

        List<BacksiteOrder> preparingOrders = OrderRepository.getInstance().getPreparingOrders();
        if (preparingOrders.isEmpty()) {
            tvChefEmptyState.setVisibility(View.VISIBLE);
        } else {
            tvChefEmptyState.setVisibility(View.GONE);
            for (BacksiteOrder order : preparingOrders) {
                View card = createChefOrderCard(order);
                containerChefOrders.addView(card);
            }
        }
    }

    private View createChefOrderCard(BacksiteOrder order) {
        MaterialCardView card = new MaterialCardView(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, (int) (12 * getResources().getDisplayMetrics().density));
        card.setLayoutParams(params);
        card.setRadius(12 * getResources().getDisplayMetrics().density);
        card.setCardElevation(2 * getResources().getDisplayMetrics().density);
        card.setCardBackgroundColor(getColor(R.color.surface_card));
        card.setStrokeColor(getColor(R.color.divider_color));
        card.setStrokeWidth(1);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (14 * getResources().getDisplayMetrics().density);
        content.setPadding(pad, pad, pad, pad);

        // Header: Table name + Badge
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView tvTitle = new TextView(this);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        tvTitle.setLayoutParams(titleParams);
        tvTitle.setText(order.getTableDisplay() + " • " + order.getSetMeal());
        tvTitle.setTextColor(getColor(R.color.secondary));
        tvTitle.setTextSize(16);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        header.addView(tvTitle);

        TextView tvBadge = new TextView(this);
        tvBadge.setText(order.getOrderTypeLabel());
        tvBadge.setTextColor(getColor(order.isAddon() ? R.color.primary : R.color.status_pending));
        tvBadge.setTypeface(null, android.graphics.Typeface.BOLD);
        tvBadge.setTextSize(11);
        header.addView(tvBadge);
        content.addView(header);

        // Items List
        TextView tvItems = new TextView(this);
        tvItems.setText(order.getFormattedItems());
        tvItems.setTextColor(getColor(R.color.text_main));
        tvItems.setTextSize(13);
        LinearLayout.LayoutParams itemsParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        itemsParams.setMargins(0, (int) (8 * getResources().getDisplayMetrics().density), 0, (int) (12 * getResources().getDisplayMetrics().density));
        tvItems.setLayoutParams(itemsParams);
        content.addView(tvItems);

        // Action Button
        MaterialButton btnReady = new MaterialButton(this);
        btnReady.setText("Mark Ready");
        btnReady.setAllCaps(false);
        btnReady.setTypeface(null, android.graphics.Typeface.BOLD);
        btnReady.setBackgroundColor(getColor(R.color.role_staff));
        btnReady.setTextColor(getColor(R.color.white));
        btnReady.setCornerRadius((int) (8 * getResources().getDisplayMetrics().density));
        btnReady.setOnClickListener(v -> {
            order.markReady();
            BackendSyncManager.getInstance().markOrderReady(order.getOrderId(), null);
            refreshOrders();
            Toast.makeText(this, order.getTableCode() + " order marked ready! Moved to Floor Staff pickup.", Toast.LENGTH_SHORT).show();
        });
        content.addView(btnReady);

        card.addView(content);
        return card;
    }

    private void populateFloorStation() {
        populateAssistanceAlerts();
        populateTableTimers();

        if (containerFloorOrders == null) return;
        containerFloorOrders.removeAllViews();

        List<BacksiteOrder> readyOrders = OrderRepository.getInstance().getReadyOrders();
        if (readyOrders.isEmpty()) {
            tvFloorEmptyState.setVisibility(View.VISIBLE);
        } else {
            tvFloorEmptyState.setVisibility(View.GONE);
            for (BacksiteOrder order : readyOrders) {
                View card = createFloorOrderCard(order);
                containerFloorOrders.addView(card);
            }
        }
    }

    private View createFloorOrderCard(BacksiteOrder order) {
        MaterialCardView card = new MaterialCardView(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, (int) (12 * getResources().getDisplayMetrics().density));
        card.setLayoutParams(params);
        card.setRadius(12 * getResources().getDisplayMetrics().density);
        card.setCardElevation(2 * getResources().getDisplayMetrics().density);
        card.setCardBackgroundColor(getColor(R.color.surface_card));
        card.setStrokeColor(getColor(R.color.divider_color));
        card.setStrokeWidth(1);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (14 * getResources().getDisplayMetrics().density);
        content.setPadding(pad, pad, pad, pad);

        // Header: Table name + Status
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView tvTitle = new TextView(this);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        tvTitle.setLayoutParams(titleParams);
        tvTitle.setText(order.getTableDisplay() + " • " + order.getSetMeal());
        tvTitle.setTextColor(getColor(R.color.secondary));
        tvTitle.setTextSize(16);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        header.addView(tvTitle);

        TextView tvStatus = new TextView(this);
        tvStatus.setText("Ready for Delivery");
        tvStatus.setTextColor(getColor(R.color.status_ready));
        tvStatus.setTypeface(null, android.graphics.Typeface.BOLD);
        tvStatus.setTextSize(11);
        header.addView(tvStatus);
        content.addView(header);

        // Items List
        TextView tvItems = new TextView(this);
        tvItems.setText(order.getFormattedItems());
        tvItems.setTextColor(getColor(R.color.text_main));
        tvItems.setTextSize(13);
        LinearLayout.LayoutParams itemsParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        itemsParams.setMargins(0, (int) (8 * getResources().getDisplayMetrics().density), 0, (int) (12 * getResources().getDisplayMetrics().density));
        tvItems.setLayoutParams(itemsParams);
        content.addView(tvItems);

        // Action Button
        MaterialButton btnDeliver = new MaterialButton(this);
        boolean isInitial = !order.isAddon();
        btnDeliver.setText(isInitial ? "Serve & Start Timer" : "Serve to Table");
        btnDeliver.setAllCaps(false);
        btnDeliver.setTypeface(null, android.graphics.Typeface.BOLD);
        btnDeliver.setBackgroundColor(getColor(isInitial ? R.color.status_ready : R.color.primary));
        btnDeliver.setTextColor(getColor(R.color.white));
        btnDeliver.setCornerRadius((int) (8 * getResources().getDisplayMetrics().density));
        btnDeliver.setOnClickListener(v -> {
            order.markServed();
            BackendSyncManager.getInstance().markOrderDelivered(order.getOrderId(), null);
            if (isInitial) {
                TableTimerManager.getInstance().startTimer(order.getTableCode());
                Toast.makeText(this, order.getTableCode() + " delivered! 1-hour dining timer officially started ⏱️", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Add-on order delivered to " + order.getTableCode() + "!", Toast.LENGTH_SHORT).show();
            }
            refreshOrders();
        });
        content.addView(btnDeliver);

        card.addView(content);
        return card;
    }

    private void populateAssistanceAlerts() {
        if (containerAssistanceAlerts == null) return;
        containerAssistanceAlerts.removeAllViews();

        List<ServiceRequestManager.Request> requests = ServiceRequestManager.getInstance().getActiveRequests();
        if (requests.isEmpty()) {
            containerAssistanceAlerts.setVisibility(View.GONE);
            return;
        }

        containerAssistanceAlerts.setVisibility(View.VISIBLE);
        for (ServiceRequestManager.Request req : requests) {
            MaterialCardView card = new MaterialCardView(this);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            params.setMargins(0, 0, 0, (int) (8 * getResources().getDisplayMetrics().density));
            card.setLayoutParams(params);
            card.setRadius(10 * getResources().getDisplayMetrics().density);
            card.setCardElevation(2 * getResources().getDisplayMetrics().density);
            card.setCardBackgroundColor(getColor(R.color.surface_card));
            card.setStrokeColor(getColor(R.color.primary));
            card.setStrokeWidth(2);

            LinearLayout content = new LinearLayout(this);
            content.setOrientation(LinearLayout.HORIZONTAL);
            content.setGravity(android.view.Gravity.CENTER_VERTICAL);
            int pad = (int) (12 * getResources().getDisplayMetrics().density);
            content.setPadding(pad, pad, pad, pad);

            LinearLayout textCol = new LinearLayout(this);
            textCol.setOrientation(LinearLayout.VERTICAL);
            textCol.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            TextView tvHeader = new TextView(this);
            if (req.isCheckout()) {
                tvHeader.setText("🏁 " + req.getTableCode() + " Requesting Checkout");
            } else {
                tvHeader.setText("🛎️ " + req.getTableCode() + " Calling for Help");
            }
            tvHeader.setTextColor(getColor(R.color.primary));
            tvHeader.setTextSize(14);
            tvHeader.setTypeface(null, android.graphics.Typeface.BOLD);
            textCol.addView(tvHeader);

            TextView tvMsg = new TextView(this);
            if (req.isCheckout() && req.getTotalBill() > 0) {
                tvMsg.setText("Total Bill: ₱" + String.format("%,d", req.getTotalBill()) + "\nCustomer finished dining and is ready to pay.");
            } else {
                tvMsg.setText(req.getMessage() != null && !req.getMessage().isEmpty() ? req.getMessage() : "Assistance / help requested");
            }
            tvMsg.setTextColor(getColor(R.color.text_main));
            tvMsg.setTextSize(13);
            textCol.addView(tvMsg);

            content.addView(textCol);

            if (req.isCheckout()) {
                MaterialButton btnConfirmCheckout = new MaterialButton(this);
                btnConfirmCheckout.setText("Confirm Checkout");
                btnConfirmCheckout.setAllCaps(false);
                btnConfirmCheckout.setBackgroundColor(getColor(R.color.primary));
                btnConfirmCheckout.setTextColor(getColor(R.color.white));
                btnConfirmCheckout.setTextSize(11);
                btnConfirmCheckout.setCornerRadius((int) (6 * getResources().getDisplayMetrics().density));
                btnConfirmCheckout.setOnClickListener(v -> {
                    new AlertDialog.Builder(this)
                            .setTitle("Confirm Checkout for " + req.getTableCode())
                            .setMessage("Confirm customer payment and checkout for " + req.getTableCode() + "?\n\nTotal Bill: ₱"
                                    + String.format("%,d", req.getTotalBill())
                                    + "\n\nThis will stop the timer and officially complete the table's dining session.")
                            .setPositiveButton("Confirm Paid & Checkout", (d, w) -> {
                                OrderRepository.getInstance().checkoutTable(req.getTableCode());
                                TableTimerManager.getInstance().checkoutTable(req.getTableCode());
                                BackendSyncManager.getInstance().checkoutTableOnBackend(req.getTableCode(), null);
                                BackendSyncManager.getInstance().dismissRequest(req.getId(), null);
                                ServiceRequestManager.getInstance().dismissRequest(req.getId());
                                populateFloorStation();
                                Toast.makeText(this, req.getTableCode() + " checkout confirmed! Payment settled.", Toast.LENGTH_SHORT).show();
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                });
                content.addView(btnConfirmCheckout);
            } else {
                MaterialButton btnAttend = new MaterialButton(this);
                btnAttend.setText("Attended");
                btnAttend.setAllCaps(false);
                btnAttend.setBackgroundColor(getColor(R.color.status_ready));
                btnAttend.setTextColor(getColor(R.color.white));
                btnAttend.setTextSize(11);
                btnAttend.setCornerRadius((int) (6 * getResources().getDisplayMetrics().density));
                btnAttend.setOnClickListener(v -> {
                    BackendSyncManager.getInstance().dismissRequest(req.getId(), null);
                    ServiceRequestManager.getInstance().dismissRequest(req.getId());
                    populateFloorStation();
                    Toast.makeText(this, req.getTableCode() + " request marked attended!", Toast.LENGTH_SHORT).show();
                });
                content.addView(btnAttend);
            }

            card.addView(content);
            containerAssistanceAlerts.addView(card);
        }
    }

    private void populateTableTimers() {
        if (containerTableTimers == null) return;
        containerTableTimers.removeAllViews();

        List<BacksiteOrder> orders = OrderRepository.getInstance().getAllOrders();
        Set<String> processedTables = new HashSet<>();
        List<String> activeTables = new ArrayList<>();

        for (BacksiteOrder o : orders) {
            String table = o.getTableCode();
            if (!processedTables.contains(table)) {
                processedTables.add(table);
                if (!TableTimerManager.getInstance().isTableCheckedOut(table)) {
                    activeTables.add(table);
                }
            }
        }

        if (activeTables.isEmpty()) {
            if (tvNoActiveTimers != null) tvNoActiveTimers.setVisibility(View.VISIBLE);
            return;
        }

        if (tvNoActiveTimers != null) tvNoActiveTimers.setVisibility(View.GONE);

        for (String table : activeTables) {
            String setInfo = "";
            for (BacksiteOrder o : orders) {
                if (table.equalsIgnoreCase(o.getTableCode()) && !o.isAddon()) {
                    setInfo = o.getHeadCount() + " Pax • " + o.getSetMeal();
                    break;
                }
            }

            boolean isStarted = TableTimerManager.getInstance().isTimerStarted(table);
            boolean isExpired = TableTimerManager.getInstance().isExpired(table);
            long remaining = TableTimerManager.getInstance().getRemainingMillis(table);
            String timeStr = TableTimerManager.getInstance().getFormattedRemainingTime(table);

            MaterialCardView card = new MaterialCardView(this);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            params.setMargins(0, 0, 0, (int) (8 * getResources().getDisplayMetrics().density));
            card.setLayoutParams(params);
            card.setRadius(10 * getResources().getDisplayMetrics().density);
            card.setCardElevation(2 * getResources().getDisplayMetrics().density);
            card.setCardBackgroundColor(getColor(R.color.surface_card));

            int strokeColor;
            if (isExpired) {
                strokeColor = getColor(R.color.primary);
            } else if (remaining <= 10 * 60 * 1000L && isStarted) {
                strokeColor = getColor(R.color.primary);
            } else if (isStarted) {
                strokeColor = getColor(R.color.status_ready);
            } else {
                strokeColor = getColor(R.color.divider_color);
            }
            card.setStrokeColor(strokeColor);
            card.setStrokeWidth(2);

            LinearLayout content = new LinearLayout(this);
            content.setOrientation(LinearLayout.HORIZONTAL);
            content.setGravity(android.view.Gravity.CENTER_VERTICAL);
            int pad = (int) (12 * getResources().getDisplayMetrics().density);
            content.setPadding(pad, pad, pad, pad);

            LinearLayout textCol = new LinearLayout(this);
            textCol.setOrientation(LinearLayout.VERTICAL);
            textCol.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            int extFee = TableTimerManager.getInstance().getExtensionFee(table);
            TextView tvTitle = new TextView(this);
            tvTitle.setText(table + (setInfo.isEmpty() ? "" : " • " + setInfo) + (extFee > 0 ? " (Extra: +₱" + extFee + ")" : ""));
            tvTitle.setTextColor(getColor(R.color.secondary));
            tvTitle.setTextSize(14);
            tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
            textCol.addView(tvTitle);

            TextView tvTimerStatus = new TextView(this);
            if (!isStarted) {
                tvTimerStatus.setText("⏳ Waiting for initial delivery (60:00 limit)");
                tvTimerStatus.setTextColor(getColor(R.color.text_muted));
            } else if (isExpired) {
                tvTimerStatus.setText("⏰ 00:00 EXPIRED • Dining time exceeded");
                tvTimerStatus.setTextColor(getColor(R.color.primary));
                tvTimerStatus.setTypeface(null, android.graphics.Typeface.BOLD);
            } else if (remaining <= 10 * 60 * 1000L) {
                tvTimerStatus.setText("⚠️ " + timeStr + " remaining (Less than 10 mins!)");
                tvTimerStatus.setTextColor(getColor(R.color.primary));
                tvTimerStatus.setTypeface(null, android.graphics.Typeface.BOLD);
            } else {
                tvTimerStatus.setText("⏱️ " + timeStr + " remaining");
                tvTimerStatus.setTextColor(getColor(R.color.status_ready));
                tvTimerStatus.setTypeface(null, android.graphics.Typeface.BOLD);
            }
            tvTimerStatus.setTextSize(13);
            textCol.addView(tvTimerStatus);
            content.addView(textCol);

            MaterialButton btnCheckoutQuick = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
            btnCheckoutQuick.setText("Checkout");
            btnCheckoutQuick.setAllCaps(false);
            btnCheckoutQuick.setTextSize(11);
            btnCheckoutQuick.setStrokeColor(android.content.res.ColorStateList.valueOf(getColor(R.color.primary)));
            btnCheckoutQuick.setTextColor(getColor(R.color.primary));
            btnCheckoutQuick.setCornerRadius((int) (6 * getResources().getDisplayMetrics().density));
            LinearLayout.LayoutParams coParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, (int) (36 * getResources().getDisplayMetrics().density));
            btnCheckoutQuick.setLayoutParams(coParams);
            btnCheckoutQuick.setOnClickListener(v -> {
                new AlertDialog.Builder(this)
                        .setTitle("Checkout " + table)
                        .setMessage("Officially checkout " + table + " and free the table?")
                        .setPositiveButton("Confirm Checkout", (d, w) -> {
                            OrderRepository.getInstance().checkoutTable(table);
                            TableTimerManager.getInstance().checkoutTable(table);
                            BackendSyncManager.getInstance().checkoutTableOnBackend(table, null);
                            populateFloorStation();
                            Toast.makeText(this, table + " officially checked out! Table is now free.", Toast.LENGTH_SHORT).show();
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            });
            content.addView(btnCheckoutQuick);

            card.addView(content);
            containerTableTimers.addView(card);
        }
    }

    private void showClearDatabaseConfirmation() {
        new AlertDialog.Builder(this)
                .setTitle("Clear Database & Sessions")
                .setMessage("Are you sure you want to reset all data?\n\nThis will remove all customer orders, reset dining timers, and return the system to a clean fresh state (0 customers / 0 orders).")
                .setPositiveButton("Reset Everything", (dialog, which) -> {
                    OrderRepository.getInstance().clearAll();
                    TableTimerManager.getInstance().resetAll();
                    ServiceRequestManager.getInstance().clearAll();
                    BackendSyncManager.getInstance().resetBackend(null);
                    refreshOrders();
                    Toast.makeText(this, "Database cleaned! 0 orders, 0 active tables.", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showStaffExtendTimeDialog() {
        String[] tables = new String[] { "Table 1", "Table 2", "Table 3", "Table 4", "Table 5" };
        new AlertDialog.Builder(this)
                .setTitle("Select Table to Extend")
                .setItems(tables, (dialog, tableIdx) -> {
                    String selectedTable = tables[tableIdx];
                    showStaffDurationDialog(selectedTable);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showStaffDurationDialog(String tableCode) {
        String[] durations = new String[] { "+30 Minutes (+₱180)", "+1 Hour (60 mins) (+₱300)" };
        long[] millis = new long[] { 30 * 60 * 1000L, 60 * 60 * 1000L };
        int[] prices = new int[] { 180, 300 };

        new AlertDialog.Builder(this)
                .setTitle("Extend " + tableCode + " Dining Time")
                .setItems(durations, (dialog, which) -> {
                    long addedMillis = millis[which];
                    int fee = prices[which];
                    String label = durations[which];

                    new AlertDialog.Builder(this)
                            .setTitle("Confirm Extension for " + tableCode)
                            .setMessage("Extend " + tableCode + " dining time by " + label.split(" ")[0] + "?\n\n"
                                    + "Additional Charge: ₱" + fee + "\n"
                                    + "This fee will be added to the customer's bill.")
                            .setPositiveButton("Confirm (+₱" + fee + ")", (d, w) -> {
                                TableTimerManager.getInstance().extendTime(tableCode, addedMillis, fee);
                                BackendSyncManager.getInstance().extendTimer(tableCode, addedMillis, null);
                                populateTableTimers();
                                String formatted = TableTimerManager.getInstance().getFormattedRemainingTime(tableCode);
                                Toast.makeText(this, tableCode + " extended by " + label.split(" ")[0] + " (+₱" + fee + " added)! (Time remaining: " + formatted + ")", Toast.LENGTH_SHORT).show();
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showStaffCheckoutDialog() {
        String[] tables = new String[] { "Table 1", "Table 2", "Table 3", "Table 4", "Table 5" };
        new AlertDialog.Builder(this)
                .setTitle("Select Table to Checkout")
                .setItems(tables, (dialog, tableIdx) -> {
                    String selectedTable = tables[tableIdx];
                    new AlertDialog.Builder(this)
                            .setTitle("Checkout " + selectedTable)
                            .setMessage("Are you sure you want to officially checkout " + selectedTable + "?\n\nThis will stop the dining timer, mark orders as checked out, and free the table for new customers.")
                            .setPositiveButton("Confirm Checkout", (d, w) -> {
                                OrderRepository.getInstance().checkoutTable(selectedTable);
                                TableTimerManager.getInstance().checkoutTable(selectedTable);
                                BackendSyncManager.getInstance().checkoutTableOnBackend(selectedTable, null);
                                refreshOrders();
                                Toast.makeText(this, selectedTable + " officially checked out! Table is now free.", Toast.LENGTH_SHORT).show();
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
