package com.example.samgyupsalorderingsystem;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

public class SetSelectionActivity extends AppCompatActivity {

    public static final String EXTRA_TABLE_CODE = "EXTRA_TABLE_CODE";
    public static final String EXTRA_CURRENT_SET = "EXTRA_CURRENT_SET";
    public static final String EXTRA_HAS_ORDERED = "EXTRA_HAS_ORDERED";
    public static final String EXTRA_HEAD_COUNT = "EXTRA_HEAD_COUNT";

    private MaterialCardView cardSetA;
    private MaterialCardView cardSetB;
    private MaterialCardView cardSetC;

    private RadioButton rbSetA;
    private RadioButton rbSetB;
    private RadioButton rbSetC;

    private TextView tvLockedA;
    private TextView tvLockedB;

    private TextView tvTitleSetA;
    private TextView tvTitleSetB;
    private TextView tvTitleSetC;

    private TextView tvSelectedSetLabel;
    private TextView tvSelectedSetDesc;

    private TextView tvSetTableCode;
    private TextView tvPaxCount;
    private com.google.android.material.button.MaterialButton btnMinusPax;
    private com.google.android.material.button.MaterialButton btnPlusPax;
    private TextView tvLockedPax;

    private SetMeal selectedSet = SetMeal.SET_A;
    private SetMeal currentSet = null;
    private boolean hasOrdered = false;
    private String tableCode;
    private int headCount = 2;

    @Override
    protected void onResume() {
        super.onResume();
        BackendSyncManager.getInstance().syncPricing(() -> {
            updateSetCardPrices();
            updatePaxDisplay();
        });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_set_selection);

        tableCode = getIntent().getStringExtra(EXTRA_TABLE_CODE);
        if (tableCode == null || tableCode.trim().isEmpty()) {
            tableCode = "Table 1";
        }

        headCount = getIntent().getIntExtra(EXTRA_HEAD_COUNT, 2);
        if (headCount < 1) headCount = 1;

        String currentSetStr = getIntent().getStringExtra(EXTRA_CURRENT_SET);
        if (currentSetStr != null && !currentSetStr.trim().isEmpty()) {
            currentSet = SetMeal.fromString(currentSetStr);
            selectedSet = currentSet;
        }

        hasOrdered = getIntent().getBooleanExtra(EXTRA_HAS_ORDERED, false);

        tvSetTableCode = findViewById(R.id.tvSetTableCode);

        // Pax controls
        tvPaxCount = findViewById(R.id.tvPaxCount);
        btnMinusPax = findViewById(R.id.btnMinusPax);
        btnPlusPax = findViewById(R.id.btnPlusPax);
        tvLockedPax = findViewById(R.id.tvLockedPax);

        cardSetA = findViewById(R.id.cardSetA);
        cardSetB = findViewById(R.id.cardSetB);
        cardSetC = findViewById(R.id.cardSetC);

        rbSetA = findViewById(R.id.rbSetA);
        rbSetB = findViewById(R.id.rbSetB);
        rbSetC = findViewById(R.id.rbSetC);

        tvTitleSetA = findViewById(R.id.tvTitleSetA);
        tvTitleSetB = findViewById(R.id.tvTitleSetB);
        tvTitleSetC = findViewById(R.id.tvTitleSetC);

        tvLockedA = findViewById(R.id.tvLockedA);
        tvLockedB = findViewById(R.id.tvLockedB);

        tvSelectedSetLabel = findViewById(R.id.tvSelectedSetLabel);
        tvSelectedSetDesc = findViewById(R.id.tvSelectedSetDesc);

        MaterialButton btnConfirmSet = findViewById(R.id.btnConfirmSet);

        updateSetCardPrices();
        updatePaxDisplay();

        btnMinusPax.setOnClickListener(v -> {
            if (headCount > 1) {
                headCount--;
                updatePaxDisplay();
            }
        });

        btnPlusPax.setOnClickListener(v -> {
            if (headCount < 30) {
                headCount++;
                updatePaxDisplay();
            }
        });

        if (hasOrdered) {
            btnMinusPax.setEnabled(false);
            btnPlusPax.setEnabled(false);
            tvLockedPax.setVisibility(View.VISIBLE);
        }

        // Configure downgrade restrictions if customer has already ordered
        configureDowngradeRestrictions();

        cardSetA.setOnClickListener(v -> {
            if (hasOrdered && currentSet != null && SetMeal.SET_A.isDowngradeFrom(currentSet)) {
                Toast.makeText(this, "Cannot downgrade to a lower set meal once an order has been placed.", Toast.LENGTH_LONG).show();
                return;
            }
            selectSet(SetMeal.SET_A);
        });

        cardSetB.setOnClickListener(v -> {
            if (hasOrdered && currentSet != null && SetMeal.SET_B.isDowngradeFrom(currentSet)) {
                Toast.makeText(this, "Cannot downgrade to a lower set meal once an order has been placed.", Toast.LENGTH_LONG).show();
                return;
            }
            selectSet(SetMeal.SET_B);
        });

        cardSetC.setOnClickListener(v -> selectSet(SetMeal.SET_C));

        // Initialize selection with the customer's current set
        selectSet(selectedSet);

        btnConfirmSet.setOnClickListener(v -> {
            boolean alreadyOrdered = hasOrdered || OrderRepository.getInstance().hasInitialOrder(tableCode);
            if (!alreadyOrdered) {
                TableTimerManager.getInstance().resetTable(tableCode);
                BackendSyncManager.getInstance().resetTableSession(tableCode, null);
            }
            Intent intent = new Intent(this, MenuActivity.class);
            intent.putExtra(MenuActivity.EXTRA_TABLE_CODE, tableCode);
            intent.putExtra(MenuActivity.EXTRA_HEAD_COUNT, headCount);
            intent.putExtra(MenuActivity.EXTRA_SET_MEAL, selectedSet.name());
            intent.putExtra(MenuActivity.EXTRA_HAS_ORDERED, alreadyOrdered);
            startActivity(intent);
            finish();
        });
    }

    private void updateSetCardPrices() {
        if (tvTitleSetA != null) tvTitleSetA.setText("Set Meal A • ₱" + SetMeal.SET_A.getPricePerPax() + " / guest");
        if (tvTitleSetB != null) tvTitleSetB.setText("Set Meal B • ₱" + SetMeal.SET_B.getPricePerPax() + " / guest");
        if (tvTitleSetC != null) tvTitleSetC.setText("Set Meal C • ₱" + SetMeal.SET_C.getPricePerPax() + " / guest");
        if (rbSetA != null) rbSetA.setText("");
        if (rbSetB != null) rbSetB.setText("");
        if (rbSetC != null) rbSetC.setText("");
    }

    private void updatePaxDisplay() {
        if (tvPaxCount != null) {
            tvPaxCount.setText(headCount + (headCount == 1 ? " Guest" : " Guests"));
        }
        if (tvSetTableCode != null) {
            tvSetTableCode.setText(tableCode + " • " + headCount + (headCount == 1 ? " Guest" : " Guests"));
        }
        if (selectedSet != null) {
            selectSet(selectedSet);
        }
    }

    private void configureDowngradeRestrictions() {
        if (!hasOrdered || currentSet == null) {
            return;
        }

        if (SetMeal.SET_A.isDowngradeFrom(currentSet)) {
            tvLockedA.setVisibility(View.VISIBLE);
            cardSetA.setAlpha(0.6f);
        }

        if (SetMeal.SET_B.isDowngradeFrom(currentSet)) {
            tvLockedB.setVisibility(View.VISIBLE);
            cardSetB.setAlpha(0.6f);
        }
    }

    private void selectSet(SetMeal set) {
        selectedSet = set;
        if (set == null || rbSetA == null || cardSetA == null) return;

        rbSetA.setChecked(set == SetMeal.SET_A);
        rbSetB.setChecked(set == SetMeal.SET_B);
        rbSetC.setChecked(set == SetMeal.SET_C);

        int activeStrokeColor = getColor(R.color.primary);
        int inactiveStrokeColor = getColor(R.color.divider_color);

        cardSetA.setStrokeColor(set == SetMeal.SET_A ? activeStrokeColor : inactiveStrokeColor);
        cardSetA.setStrokeWidth(set == SetMeal.SET_A ? 4 : 1);

        cardSetB.setStrokeColor(set == SetMeal.SET_B ? activeStrokeColor : inactiveStrokeColor);
        cardSetB.setStrokeWidth(set == SetMeal.SET_B ? 4 : 1);

        cardSetC.setStrokeColor(set == SetMeal.SET_C ? activeStrokeColor : inactiveStrokeColor);
        cardSetC.setStrokeWidth(set == SetMeal.SET_C ? 4 : 1);

        int pricePerPax = set.getPricePerPax();
        int total = pricePerPax * headCount;
        if (tvSelectedSetLabel != null) {
            tvSelectedSetLabel.setText("Selected: " + set.getCode() + " (₱" + pricePerPax + " / pax)");
        }
        if (tvSelectedSetDesc != null) {
            tvSelectedSetDesc.setText(set.getTitle() + " • ₱" + pricePerPax + " × " + headCount + " Pax = ₱" + String.format("%,d", total));
        }
    }
}
