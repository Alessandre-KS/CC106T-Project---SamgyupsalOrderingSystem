package com.example.samgyupsalorderingsystem;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MenuActivity extends AppCompatActivity {

    public static final String EXTRA_TABLE_CODE = "EXTRA_TABLE_CODE";
    public static final String EXTRA_SET_MEAL = "EXTRA_SET_MEAL";
    public static final String EXTRA_HAS_ORDERED = "EXTRA_HAS_ORDERED";
    public static final String EXTRA_HEAD_COUNT = "EXTRA_HEAD_COUNT";

    private String tableCode;
    private int headCount = 2;
    private SetMeal currentSet;
    private boolean hasOrdered = false;

    // 1-Hour Dining Timer Views
    private TextView tvTimerIcon;
    private TextView tvTimerTitle;
    private TextView tvTimerSubtitle;
    private TextView tvTimerCountdown;

    private Handler timerHandler;
    private Runnable timerRunnable;

    // Itemized cart mapping item name to quantity
    private final Map<String, Integer> cart = new LinkedHashMap<>();
    private final List<TextView> qtyViews = new ArrayList<>();

    private TextView tvOrderCount;
    private TextView tvOrderPreview;

    private AlertDialog waitingCheckoutDialog;
    private boolean checkoutCompleted = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_menu);

        tableCode = getIntent().getStringExtra(EXTRA_TABLE_CODE);
        if (tableCode == null || tableCode.trim().isEmpty()) {
            tableCode = "Table 1";
        }

        headCount = getIntent().getIntExtra(EXTRA_HEAD_COUNT, 2);
        if (headCount < 1) headCount = 1;

        String setMealStr = getIntent().getStringExtra(EXTRA_SET_MEAL);
        currentSet = SetMeal.fromString(setMealStr);
        hasOrdered = getIntent().getBooleanExtra(EXTRA_HAS_ORDERED, false);
        if (!hasOrdered && !OrderRepository.getInstance().hasInitialOrder(tableCode)) {
            TableTimerManager.getInstance().resetTable(tableCode);
            BackendSyncManager.getInstance().resetTableSession(tableCode, null);
        }

        initViews();
        setupBackPressHandling();
    }

    private void initViews() {
        TextView tvTableNumber = findViewById(R.id.tvTableNumber);
        TextView tvSetMealBadge = findViewById(R.id.tvSetMealBadge);
        tvOrderCount = findViewById(R.id.tvOrderCount);
        tvOrderPreview = findViewById(R.id.tvOrderPreview);

        MaterialButton btnChangeSet = findViewById(R.id.btnChangeSet);
        MaterialButton btnCallStaff = findViewById(R.id.btnCallStaff);
        MaterialButton btnExitMenu = findViewById(R.id.btnExitMenu);
        MaterialButton btnSubmitOrder = findViewById(R.id.btnSubmitOrder);

        tvTableNumber.setText(tableCode + " • " + headCount + " Pax");
        tvSetMealBadge.setText(currentSet.getCode() + " (₱" + currentSet.getPricePerPax() + "/pax) • " + currentSet.getTitle());

        // 1-Hour Dining Timer Views
        tvTimerIcon = findViewById(R.id.tvTimerIcon);
        tvTimerTitle = findViewById(R.id.tvTimerTitle);
        tvTimerSubtitle = findViewById(R.id.tvTimerSubtitle);
        tvTimerCountdown = findViewById(R.id.tvTimerCountdown);
        MaterialButton btnExtendTimer = findViewById(R.id.btnExtendTimer);
        if (btnExtendTimer != null) {
            btnExtendTimer.setOnClickListener(v -> showExtendTimeDialog());
        }

        startTimerUpdates();

        // --- Meats ---
        setupItemControls(R.id.btnMinusNormalPork, R.id.btnPlusNormalPork, R.id.tvQtyNormalPork,
                "Plain Pork Belly (Samgyupsal)", true, null);

        setupItemControls(R.id.btnMinusSpicyPork, R.id.btnPlusSpicyPork, R.id.tvQtySpicyPork,
                "Spicy Pork (Yangnyeom Bulgogi)", true, null);

        setupItemControls(R.id.btnMinusSweetPork, R.id.btnPlusSweetPork, R.id.tvQtySweetPork,
                "Sweet Soy Marinated Pork", true, null);

        // Beef items (Set C only)
        TextView tvStatusPlainBeef = findViewById(R.id.tvStatusPlainBeef);
        TextView tvStatusBulgogiBeef = findViewById(R.id.tvStatusBulgogiBeef);

        if (currentSet.hasBeef()) {
            tvStatusPlainBeef.setText("Included in your " + currentSet.getCode() + " • Premium cut");
            tvStatusPlainBeef.setTextColor(getColor(R.color.status_ready));
            tvStatusBulgogiBeef.setText("Included in your " + currentSet.getCode() + " • Sweet soy marinade");
            tvStatusBulgogiBeef.setTextColor(getColor(R.color.status_ready));

            setupItemControls(R.id.btnMinusPlainBeef, R.id.btnPlusPlainBeef, R.id.tvQtyPlainBeef,
                    "Plain Beef Samgyup (Woosamgyup)", true, null);
            setupItemControls(R.id.btnMinusBulgogiBeef, R.id.btnPlusBulgogiBeef, R.id.tvQtyBulgogiBeef,
                    "Sweet Marinated Beef (Bulgogi)", true, null);
        } else {
            tvStatusPlainBeef.setText("Requires SET MEAL C (Pork & Beef)");
            tvStatusPlainBeef.setTextColor(getColor(R.color.primary));
            tvStatusBulgogiBeef.setText("Requires SET MEAL C (Pork & Beef)");
            tvStatusBulgogiBeef.setTextColor(getColor(R.color.primary));

            setupItemControls(R.id.btnMinusPlainBeef, R.id.btnPlusPlainBeef, R.id.tvQtyPlainBeef,
                    "Plain Beef Samgyup (Woosamgyup)", false, "SET MEAL C");
            setupItemControls(R.id.btnMinusBulgogiBeef, R.id.btnPlusBulgogiBeef, R.id.tvQtyBulgogiBeef,
                    "Sweet Marinated Beef (Bulgogi)", false, "SET MEAL C");
        }

        // --- Authentic PH Samgyupsal Side Dishes ---
        // Customer can order ONLY lettuce or any desired combination!
        setupItemControls(R.id.btnMinusLettuce, R.id.btnPlusLettuce, R.id.tvQtyLettuce,
                "Fresh Lettuce Basket (Sangchu)", true, null);

        setupItemControls(R.id.btnMinusKimchi, R.id.btnPlusKimchi, R.id.tvQtyKimchi,
                "Kimchi", true, null);

        setupItemControls(R.id.btnMinusPotatoes, R.id.btnPlusPotatoes, R.id.tvQtyPotatoes,
                "Sweet Marble Potatoes (Gamja Jorim)", true, null);

        setupItemControls(R.id.btnMinusJapchae, R.id.btnPlusJapchae, R.id.tvQtyJapchae,
                "Korean Japchae (Glass Noodles)", true, null);

        setupItemControls(R.id.btnMinusPamuchim, R.id.btnPlusPamuchim, R.id.tvQtyPamuchim,
                "Scallion Green Onion Salad (Pamuchim)", true, null);

        setupItemControls(R.id.btnMinusRice, R.id.btnPlusRice, R.id.tvQtyRice,
                "Steamed White Rice (Bap)", true, null);

        setupItemControls(R.id.btnMinusSsamjang, R.id.btnPlusSsamjang, R.id.tvQtySsamjang,
                "Ssamjang & Sesame Oil Dip", true, null);

        // Cheese Dip (Sets B & C only)
        TextView tvCheeseStatus = findViewById(R.id.tvCheeseStatus);
        if (currentSet.hasCheese()) {
            tvCheeseStatus.setText("Included in your " + currentSet.getCode() + " • Cheese dip");
            tvCheeseStatus.setTextColor(getColor(R.color.status_ready));
            setupItemControls(R.id.btnMinusCheese, R.id.btnPlusCheese, R.id.tvQtyCheese,
                    "Cheese Dip", true, null);
        } else {
            tvCheeseStatus.setText("Requires SET MEAL B or C");
            tvCheeseStatus.setTextColor(getColor(R.color.primary));
            setupItemControls(R.id.btnMinusCheese, R.id.btnPlusCheese, R.id.tvQtyCheese,
                    "Cheese Dip", false, "SET MEAL B or C");
        }

        // --- Bottom Order Actions ---
        btnSubmitOrder.setOnClickListener(v -> {
            if (cart.isEmpty()) {
                Toast.makeText(this, "Please add items to your order first.", Toast.LENGTH_SHORT).show();
            } else {
                showOrderConfirmation();
            }
        });

        // Change Set button
        btnChangeSet.setOnClickListener(v -> {
            Intent intent = new Intent(this, SetSelectionActivity.class);
            intent.putExtra(SetSelectionActivity.EXTRA_TABLE_CODE, tableCode);
            intent.putExtra(SetSelectionActivity.EXTRA_HEAD_COUNT, headCount);
            intent.putExtra(SetSelectionActivity.EXTRA_CURRENT_SET, currentSet.name());
            intent.putExtra(SetSelectionActivity.EXTRA_HAS_ORDERED, hasOrdered);
            startActivity(intent);
            finish();
        });

        // Call Staff button (FR-009)
        if (btnCallStaff != null) {
            btnCallStaff.setOnClickListener(v -> showCallStaffDialog());
        }

        // Exit / Checkout Table
        btnExitMenu.setOnClickListener(v -> showExitOrCheckoutDialog());
    }

    private void setupItemControls(int minusBtnId, int plusBtnId, int qtyViewId,
                                   String itemName, boolean isAllowedInSet, String requiredSet) {
        MaterialButton btnMinus = findViewById(minusBtnId);
        MaterialButton btnPlus = findViewById(plusBtnId);
        TextView tvQty = findViewById(qtyViewId);

        qtyViews.add(tvQty);

        btnPlus.setOnClickListener(v -> {
            if (!isAllowedInSet) {
                promptUpgrade(itemName, requiredSet);
            } else {
                updateQuantity(itemName, 1, tvQty);
            }
        });

        btnMinus.setOnClickListener(v -> {
            updateQuantity(itemName, -1, tvQty);
        });
    }

    private void updateQuantity(String itemName, int delta, TextView qtyView) {
        int current = cart.getOrDefault(itemName, 0);
        int newQty = Math.max(0, current + delta);

        if (newQty == 0) {
            cart.remove(itemName);
        } else {
            cart.put(itemName, newQty);
        }

        if (qtyView != null) {
            qtyView.setText(String.valueOf(newQty));
        }

        updateBottomSummary();
    }

    private void updateBottomSummary() {
        int totalItems = 0;
        StringBuilder preview = new StringBuilder();

        for (Map.Entry<String, Integer> entry : cart.entrySet()) {
            totalItems += entry.getValue();
            if (preview.length() > 0) preview.append(", ");
            preview.append(entry.getValue()).append("x ").append(entry.getKey());
        }

        tvOrderCount.setText("Total Items: " + totalItems);
        if (totalItems > 0) {
            tvOrderPreview.setText(preview.toString());
        } else {
            tvOrderPreview.setText("No items selected yet");
        }
    }

    private void showOrderConfirmation() {
        if (TableTimerManager.getInstance().isExpired(tableCode)) {
            new AlertDialog.Builder(this)
                    .setTitle("1-Hour Dining Time Ended")
                    .setMessage("Your table's unlimited dining session has ended. Would you like to extend your dining time or settle your bill?")
                    .setPositiveButton("Extend Time ⏱️", (dialog, which) -> showExtendTimeDialog())
                    .setNegativeButton("Settle Bill", null)
                    .show();
            return;
        }

        StringBuilder orderList = new StringBuilder();
        for (Map.Entry<String, Integer> entry : cart.entrySet()) {
            orderList.append("• ").append(entry.getValue()).append("x ").append(entry.getKey()).append("\n");
        }

        String dialogTitle = hasOrdered ? "Send Add-on Order to Kitchen?" : "Send Order to Kitchen?";
        String orderTypeNote = hasOrdered ? "\nType: [ADD-ON ORDER] (Separate ticket)" : "";

        new AlertDialog.Builder(this)
                .setTitle(dialogTitle)
                .setMessage("Table: " + tableCode + " (" + headCount + " Pax • " + currentSet.getCode() + ")" + orderTypeNote + "\n\nItems to prepare:\n" + orderList.toString().trim())
                .setPositiveButton("Send Order", (dialog, which) -> {
                    boolean wasAddon = hasOrdered;
                    hasOrdered = true;

                    List<String> orderItemsList = new ArrayList<>();
                    for (Map.Entry<String, Integer> entry : cart.entrySet()) {
                        orderItemsList.add(entry.getValue() + "x " + entry.getKey());
                    }
                    BacksiteOrder newOrder = OrderRepository.getInstance().createOrder(
                            tableCode, headCount, currentSet.getCode(), orderItemsList, wasAddon);
                    BackendSyncManager.getInstance().sendOrder(newOrder, null);

                    String toastMsg = wasAddon
                            ? "Add-on order sent to kitchen as a separate ticket for " + tableCode + " (" + headCount + " Pax)!"
                            : "Order sent to kitchen for " + tableCode + " (" + headCount + " Pax)!";
                    Toast.makeText(this, toastMsg, Toast.LENGTH_LONG).show();
                    cart.clear();
                    for (TextView tv : qtyViews) {
                        tv.setText("0");
                    }
                    updateBottomSummary();
                })
                .setNegativeButton("Add More", null)
                .show();
    }

    private void showExtendTimeDialog() {
        String[] options = new String[] {
                "+15 Minutes (+₱100)",
                "+30 Minutes (+₱180)",
                "+1 Hour (60 mins) (+₱300)"
        };
        long[] durations = new long[] {
                15 * 60 * 1000L,
                30 * 60 * 1000L,
                60 * 60 * 1000L
        };
        int[] prices = new int[] { 100, 180, 300 };

        new AlertDialog.Builder(this)
                .setTitle("Extend Dining Time ⏱️")
                .setItems(options, (dialog, which) -> {
                    long addedMillis = durations[which];
                    String label = options[which];
                    int fee = prices[which];

                    new AlertDialog.Builder(this)
                            .setTitle("Confirm Time Extension ⏱️")
                            .setMessage("Extend dining session by " + label.split(" ")[0] + "?\n\n"
                                    + "Additional Charge: ₱" + fee + "\n"
                                    + "This fee will be added to your table's final checkout bill.")
                            .setPositiveButton("Confirm (+₱" + fee + ")", (confirmDialog, btnWhich) -> {
                                TableTimerManager.getInstance().extendTime(tableCode, addedMillis, fee);
                                BackendSyncManager.getInstance().extendTimer(tableCode, addedMillis, null);
                                updateTimerBanner();

                                boolean started = TableTimerManager.getInstance().isTimerStarted(tableCode);
                                String toastMsg = started
                                        ? "Dining session extended by " + label.split(" ")[0] + " (+₱" + fee + " added)! ⏱️"
                                        : "Extension queued! (" + label.split(" ")[0] + " +₱" + fee + " will be applied when food arrives)";
                                Toast.makeText(this, toastMsg, Toast.LENGTH_SHORT).show();
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void startTimerUpdates() {
        timerHandler = new Handler(Looper.getMainLooper());
        timerRunnable = new Runnable() {
            private int tick = 0;
            @Override
            public void run() {
                updateTimerBanner();
                tick++;
                if (tick % 2 == 0) {
                    BackendSyncManager.getInstance().syncTimer(tableCode, (started, remaining, formatted, expired) -> {
                        updateTimerBanner();
                    });
                }
                if (timerHandler != null) {
                    timerHandler.postDelayed(this, 1000);
                }
            }
        };
        timerHandler.post(timerRunnable);
    }

    private void updateTimerBanner() {
        if (tvTimerCountdown == null) return;

        boolean started = TableTimerManager.getInstance().isTimerStarted(tableCode);
        boolean isCheckedOut = TableTimerManager.getInstance().isTableCheckedOut(tableCode);
        boolean hasActiveDiningParty = hasOrdered || OrderRepository.getInstance().hasInitialOrder(tableCode) || waitingCheckoutDialog != null;

        if (isCheckedOut && hasActiveDiningParty) {
            tvTimerIcon.setText("●");
            tvTimerTitle.setText("Session Complete");
            tvTimerSubtitle.setText("Table officially checked out");
            tvTimerCountdown.setText("PAID");
            tvTimerCountdown.setTextColor(getColor(R.color.status_ready));

            if (!checkoutCompleted) {
                checkoutCompleted = true;
                if (waitingCheckoutDialog != null && waitingCheckoutDialog.isShowing()) {
                    waitingCheckoutDialog.dismiss();
                }
                new AlertDialog.Builder(this)
                        .setTitle("Checkout Confirmed")
                        .setMessage("Floor staff has confirmed your checkout and payment.\n\nThank you for dining at Mukbang Samgyupsal!")
                        .setCancelable(false)
                        .setPositiveButton("Finish", (d, w) -> {
                            OrderRepository.getInstance().checkoutTable(tableCode);
                            TableTimerManager.getInstance().checkoutTable(tableCode);
                            finish();
                        })
                        .show();
            }
        } else if (isCheckedOut && !hasActiveDiningParty) {
            // Stale checkout state from previous dining party; clear it
            TableTimerManager.getInstance().resetTable(tableCode);
            BackendSyncManager.getInstance().resetTableSession(tableCode, null);
            tvTimerIcon.setText("⏳");
            tvTimerTitle.setText("Dining Timer");
            tvTimerSubtitle.setText("Starts when first food order arrives");
            tvTimerCountdown.setText(TableTimerManager.getInstance().getFormattedRemainingTime(tableCode));
            tvTimerCountdown.setTextColor(getColor(R.color.text_muted));
        } else if (!started) {
            tvTimerIcon.setText("⏳");
            tvTimerTitle.setText("Dining Timer");
            tvTimerSubtitle.setText("Starts when first food order arrives");
            tvTimerCountdown.setText(TableTimerManager.getInstance().getFormattedRemainingTime(tableCode));
            tvTimerCountdown.setTextColor(getColor(R.color.text_muted));
        } else if (TableTimerManager.getInstance().isExpired(tableCode)) {
            tvTimerIcon.setText("●");
            tvTimerTitle.setText("Dining Time Expired");
            tvTimerSubtitle.setText("Tap Extend to add more time");
            tvTimerCountdown.setText("00:00");
            tvTimerCountdown.setTextColor(getColor(R.color.primary));
        } else {
            long remaining = TableTimerManager.getInstance().getRemainingMillis(tableCode);
            tvTimerCountdown.setText(TableTimerManager.getInstance().getFormattedRemainingTime(tableCode));
            if (remaining <= 10 * 60 * 1000L) { // Less than 10 mins remaining
                tvTimerIcon.setText("●");
                tvTimerTitle.setText("Time Ending Soon");
                tvTimerSubtitle.setText("Less than 10 minutes remaining");
                tvTimerCountdown.setTextColor(getColor(R.color.primary));
            } else {
                tvTimerIcon.setText("●");
                tvTimerTitle.setText("Dining Time");
                tvTimerSubtitle.setText("Unlimited session active");
                tvTimerCountdown.setTextColor(getColor(R.color.status_ready));
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (timerHandler != null && timerRunnable != null) {
            timerHandler.removeCallbacks(timerRunnable);
        }
    }

    private void promptUpgrade(String itemName, String requiredSet) {
        new AlertDialog.Builder(this)
                .setTitle("Upgrade Required")
                .setMessage("\"" + itemName + "\" is not included in " + currentSet.getCode() + ".\n\nWould you like to change your meal plan to " + requiredSet + "?")
                .setPositiveButton("Change Set", (dialog, which) -> {
                    Intent intent = new Intent(this, SetSelectionActivity.class);
                    intent.putExtra(SetSelectionActivity.EXTRA_TABLE_CODE, tableCode);
                    intent.putExtra(SetSelectionActivity.EXTRA_HEAD_COUNT, headCount);
                    intent.putExtra(SetSelectionActivity.EXTRA_CURRENT_SET, currentSet.name());
                    intent.putExtra(SetSelectionActivity.EXTRA_HAS_ORDERED, hasOrdered);
                    startActivity(intent);
                    finish();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showCallStaffDialog() {
        new AlertDialog.Builder(this)
                .setTitle("🛎️ Call Staff")
                .setMessage("Call a staff member to " + tableCode + " for assistance or issues?")
                .setPositiveButton("Call Staff", (dialog, which) -> {
                    ServiceRequestManager.getInstance().requestAssistance(tableCode, "Staff Assistance / Help Needed");
                    BackendSyncManager.getInstance().sendRequest(tableCode, "Staff Assistance / Help Needed", false, 0, null);
                    Toast.makeText(this, "🛎️ Staff called to " + tableCode + "!", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showExitOrCheckoutDialog() {
        int pricePerPax = currentSet.getPricePerPax();
        int baseBill = headCount * pricePerPax;
        int extensionFee = TableTimerManager.getInstance().getExtensionFee(tableCode);
        int totalBill = baseBill + extensionFee;

        StringBuilder billSummary = new StringBuilder();
        billSummary.append("Table: ").append(tableCode).append("\n")
                .append("Meal Plan: ").append(currentSet.getCode()).append(" • ").append(currentSet.getTitle()).append("\n")
                .append("Headcount: ").append(headCount).append(" Pax (₱").append(pricePerPax).append(" / pax) = ₱").append(String.format("%,d", baseBill)).append("\n");
        if (extensionFee > 0) {
            billSummary.append("Time Extension Fee: +₱").append(String.format("%,d", extensionFee)).append("\n");
        }
        billSummary.append("Total Bill: ₱").append(String.format("%,d", totalBill)).append("\n\n")
                .append("Tap 'Officially Checkout' to send a checkout request to Floor Staff. Staff will arrive at your table to verify your bill and confirm your checkout.");

        new AlertDialog.Builder(this)
                .setTitle("🏁 Checkout & Settle Bill")
                .setMessage(billSummary)
                .setPositiveButton("Officially Checkout", (dialog, which) -> {
                    requestCustomerCheckout(totalBill);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void requestCustomerCheckout(int totalBill) {
        String reqMsg = "Checkout Request • Total: ₱" + String.format("%,d", totalBill);
        ServiceRequestManager.getInstance().requestCheckout(tableCode, totalBill);
        BackendSyncManager.getInstance().sendRequest(tableCode, reqMsg, true, totalBill, null);

        if (waitingCheckoutDialog != null && waitingCheckoutDialog.isShowing()) {
            waitingCheckoutDialog.dismiss();
        }

        waitingCheckoutDialog = new AlertDialog.Builder(this)
                .setTitle("⏳ Checkout Requested")
                .setMessage("Your checkout request has been sent to Floor Staff.\n\nTotal Bill: ₱" + String.format("%,d", totalBill)
                        + "\n\nPlease wait at " + tableCode + ". A staff member will confirm your payment and finalize your checkout.")
                .setCancelable(false)
                .setNegativeButton("Cancel Request", (dialog, which) -> {
                    waitingCheckoutDialog = null;
                    Toast.makeText(this, "Checkout request cancelled.", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void setupBackPressHandling() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                handleBackPress();
            }
        });
    }

    private void handleBackPress() {
        boolean isDining = hasOrdered
                || OrderRepository.getInstance().hasInitialOrder(tableCode)
                || TableTimerManager.getInstance().isTimerStarted(tableCode);

        if (isDining && !TableTimerManager.getInstance().isTableCheckedOut(tableCode)) {
            new AlertDialog.Builder(this)
                    .setTitle("Active Dining Session")
                    .setMessage("You are currently dining at " + tableCode + ".\n\nDo you want to return to the table entrance?\nYour active timer and meal order will continue running in the background.")
                    .setPositiveButton("Return to Entrance", (dialog, which) -> {
                        finish();
                    })
                    .setNegativeButton("Stay at Table", null)
                    .show();
        } else {
            finish();
        }
    }

    @Override
    public void onBackPressed() {
        handleBackPress();
    }
}
