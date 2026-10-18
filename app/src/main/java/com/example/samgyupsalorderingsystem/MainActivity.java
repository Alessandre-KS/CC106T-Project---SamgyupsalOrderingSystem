package com.example.samgyupsalorderingsystem;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.journeyapps.barcodescanner.CaptureActivity;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;

public class MainActivity extends AppCompatActivity {

    private MaterialCardView layoutCustomer;
    private MaterialCardView layoutStaffAdmin;
    private TextInputLayout tilTableCode;
    private TextInputEditText etTableCode;
    private TextInputLayout tilHeadCount;
    private TextInputEditText etHeadCount;
    private TextInputLayout tilId;
    private TextInputEditText etId;
    private TextInputLayout tilPasskey;
    private TextInputEditText etPasskey;
    private MaterialButton chipTable1;
    private MaterialButton chipTable2;
    private MaterialButton chipTable3;
    private MaterialButton btnProceedMenu;

    // QR Code scanner launcher using ZXing ScanContract
    private final ActivityResultLauncher<ScanOptions> qrLauncher = registerForActivityResult(
            new ScanContract(),
            result -> {
                if (result.getContents() != null) {
                    String scannedCode = result.getContents();
                    Toast.makeText(this, "QR Code Scanned: " + scannedCode, Toast.LENGTH_SHORT).show();
                    validateAndProceed(scannedCode);
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        initViews();
        setupRoleToggle();
        setupCustomerFlow();
        setupStaffAdminFlow();
    }

    @Override
    protected void onResume() {
        super.onResume();
        BackendSyncManager.getInstance().syncPricing(null);
        BackendSyncManager.getInstance().syncAuth(null);
        BackendSyncManager.getInstance().syncOrders(this::refreshOccupiedTables);
    }

    private void refreshOccupiedTables() {
        BackendSyncManager.getInstance().getOccupiedTables(occupiedSet -> {
            updateChip(chipTable1, "Table 1", occupiedSet);
            updateChip(chipTable2, "Table 2", occupiedSet);
            updateChip(chipTable3, "Table 3", occupiedSet);
        });
    }

    private void updateChip(MaterialButton chip, String tableCode, java.util.Set<String> occupiedSet) {
        if (chip == null) return;
        boolean occupied = occupiedSet != null && occupiedSet.contains(tableCode.toUpperCase(java.util.Locale.US));
        if (occupied) {
            chip.setText(tableCode + " (Occupied)");
            chip.setTextColor(getColor(R.color.primary));
            chip.setStrokeColorResource(R.color.primary);
        } else {
            chip.setText(tableCode);
            chip.setTextColor(getColor(R.color.text_main));
            chip.setStrokeColorResource(R.color.divider_color);
        }
    }

    private void initViews() {
        layoutCustomer = findViewById(R.id.layoutCustomer);
        layoutStaffAdmin = findViewById(R.id.layoutStaffAdmin);

        tilTableCode = findViewById(R.id.tilTableCode);
        etTableCode = findViewById(R.id.etTableCode);

        tilHeadCount = findViewById(R.id.tilHeadCount);
        etHeadCount = findViewById(R.id.etHeadCount);

        tilId = findViewById(R.id.tilId);
        etId = findViewById(R.id.etId);

        tilPasskey = findViewById(R.id.tilPasskey);
        etPasskey = findViewById(R.id.etPasskey);
    }

    private void setupRoleToggle() {
        MaterialButtonToggleGroup toggleGroup = findViewById(R.id.toggleRoleGroup);
        toggleGroup.check(R.id.btnTabCustomer);

        toggleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;

            if (checkedId == R.id.btnTabCustomer) {
                layoutCustomer.setVisibility(View.VISIBLE);
                layoutStaffAdmin.setVisibility(View.GONE);
                refreshOccupiedTables();
            } else if (checkedId == R.id.btnTabStaffAdmin) {
                layoutCustomer.setVisibility(View.GONE);
                layoutStaffAdmin.setVisibility(View.VISIBLE);
            }
        });
    }

    private void setupCustomerFlow() {
        // Launch camera scanner on card or button tap
        View.OnClickListener scanListener = v -> launchScanner();
        findViewById(R.id.cardScannerLaunch).setOnClickListener(scanListener);
        findViewById(R.id.btnLaunchScanner).setOnClickListener(scanListener);

        // Quick demo table buttons
        chipTable1 = findViewById(R.id.chipTable1);
        chipTable2 = findViewById(R.id.chipTable2);
        chipTable3 = findViewById(R.id.chipTable3);

        chipTable1.setOnClickListener(v -> {
            etTableCode.setText("Table 1");
            validateAndProceed("Table 1");
        });
        chipTable2.setOnClickListener(v -> {
            etTableCode.setText("Table 2");
            validateAndProceed("Table 2");
        });
        chipTable3.setOnClickListener(v -> {
            etTableCode.setText("Table 3");
            validateAndProceed("Table 3");
        });

        // Manual table code submit
        btnProceedMenu = findViewById(R.id.btnProceedMenu);
        btnProceedMenu.setOnClickListener(v -> {
            String code = etTableCode.getText() != null ? etTableCode.getText().toString().trim() : "";
            if (code.isEmpty()) {
                tilTableCode.setError(getString(R.string.err_empty_table));
            } else {
                tilTableCode.setError(null);
                validateAndProceed(code);
            }
        });
    }

    private void launchScanner() {
        ScanOptions options = new ScanOptions();
        options.setPrompt("Scan Table QR Code");
        options.setBeepEnabled(true);
        options.setOrientationLocked(false);
        options.setCaptureActivity(CaptureActivity.class);
        qrLauncher.launch(options);
    }

    private void validateAndProceed(String rawTableCode) {
        String formatted = AuthManager.formatTableCode(rawTableCode);
        if (formatted == null || formatted.trim().isEmpty()) {
            tilTableCode.setError(getString(R.string.err_empty_table));
            return;
        }

        tilTableCode.setError(null);
        if (btnProceedMenu != null) btnProceedMenu.setEnabled(false);

        BackendSyncManager.getInstance().checkTableOccupied(formatted, isOccupied -> {
            if (btnProceedMenu != null) btnProceedMenu.setEnabled(true);
            if (isOccupied) {
                tilTableCode.setError(formatted + " is currently occupied!");
                new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("Table Occupied • " + formatted)
                        .setMessage(formatted + " has an active dining session in progress.\n\nAre you returning to this table?")
                        .setPositiveButton("Resume Dining", (dialog, which) -> {
                            resumeTableSession(formatted);
                        })
                        .setNegativeButton("Choose Different Table", (dialog, which) -> {
                            refreshOccupiedTables();
                        })
                        .show();
            } else {
                navigateToMenu(formatted);
            }
        });
    }

    private void resumeTableSession(String formatted) {
        if (btnProceedMenu != null) btnProceedMenu.setEnabled(false);
        BackendSyncManager.getInstance().syncOrders(() -> {
            if (btnProceedMenu != null) btnProceedMenu.setEnabled(true);
            BacksiteOrder order = OrderRepository.getInstance().getActiveInitialOrder(formatted);
            SetMeal setMeal = SetMeal.SET_A;
            int headCount = 2;
            if (order != null) {
                setMeal = SetMeal.fromString(order.getSetMeal());
                headCount = order.getHeadCount();
            } else if (etHeadCount != null && etHeadCount.getText() != null) {
                try {
                    headCount = Math.max(1, Integer.parseInt(etHeadCount.getText().toString().trim()));
                } catch (NumberFormatException ignored) {}
            }
            Intent intent = new Intent(this, MenuActivity.class);
            intent.putExtra(MenuActivity.EXTRA_TABLE_CODE, formatted);
            intent.putExtra(MenuActivity.EXTRA_HEAD_COUNT, headCount);
            intent.putExtra(MenuActivity.EXTRA_SET_MEAL, setMeal.name());
            intent.putExtra(MenuActivity.EXTRA_HAS_ORDERED, true);
            startActivity(intent);
        });
    }

    private void navigateToMenu(String formatted) {
        int pax = 2;
        if (etHeadCount != null && etHeadCount.getText() != null) {
            try {
                pax = Math.max(1, Integer.parseInt(etHeadCount.getText().toString().trim()));
            } catch (NumberFormatException ignored) {
            }
        }

        // Clean slate for new dining party
        TableTimerManager.getInstance().resetTable(formatted);
        BackendSyncManager.getInstance().resetTableSession(formatted, null);

        Intent intent = new Intent(this, SetSelectionActivity.class);
        intent.putExtra(SetSelectionActivity.EXTRA_TABLE_CODE, formatted);
        intent.putExtra(SetSelectionActivity.EXTRA_HEAD_COUNT, pax);
        startActivity(intent);
    }

    private void setupStaffAdminFlow() {
        MaterialButton btnLogin = findViewById(R.id.btnLogin);
        btnLogin.setOnClickListener(v -> {
            String id = etId.getText() != null ? etId.getText().toString().trim() : "";
            String passkey = etPasskey.getText() != null ? etPasskey.getText().toString().trim() : "";

            tilId.setError(null);
            tilPasskey.setError(null);

            if (id.isEmpty()) {
                tilId.setError("Please enter ID");
                return;
            }
            if (passkey.isEmpty()) {
                tilPasskey.setError("Please enter Passkey");
                return;
            }

            String role = AuthManager.authenticate(id, passkey);
            if (AuthManager.ROLE_STAFF.equals(role)) {
                Toast.makeText(this, "Staff sign-in successful!", Toast.LENGTH_SHORT).show();
                Intent intent = new Intent(this, StaffActivity.class);
                startActivity(intent);
            } else if (AuthManager.ROLE_ADMIN.equals(role)) {
                Toast.makeText(this, "Admin sign-in successful!", Toast.LENGTH_SHORT).show();
                Intent intent = new Intent(this, AdminActivity.class);
                startActivity(intent);
            } else {
                tilPasskey.setError(getString(R.string.err_invalid_creds));
                Toast.makeText(this, "Invalid ID or Passkey.", Toast.LENGTH_SHORT).show();
            }
        });
    }
}