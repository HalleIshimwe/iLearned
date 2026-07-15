package com.example.ilearned;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

/**
 * ProfileActivity
 *
 * Displays:
 *  - Full name (editable — saved to Firebase Auth profile + Firestore)
 *  - Email     (read-only — from Firebase Auth)
 *
 * Actions:
 *  - Save name changes
 *  - Change password (requires current password for re-authentication)
 */
public class ProfileActivity extends AppCompatActivity {

    private EditText    editTextName;
    private TextView    textEmail;
    private Button      buttonSaveName;
    private Button      buttonChangePassword;
    private ProgressBar progressBar;

    private FirebaseAuth      mAuth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeHelper.applyTheme(this);
        setContentView(R.layout.activity_profile);

        mAuth = FirebaseAuth.getInstance();
        db    = FirebaseFirestore.getInstance();

        initViews();
        loadCurrentProfile();
        setupClickListeners();
    }

    // ─────────────────────────────────────────────────────────
    //  Views
    // ─────────────────────────────────────────────────────────

    private void initViews() {
        editTextName         = findViewById(R.id.editTextName);
        textEmail            = findViewById(R.id.textEmail);
        buttonSaveName       = findViewById(R.id.buttonSaveName);
        buttonChangePassword = findViewById(R.id.buttonChangePassword);
        progressBar          = findViewById(R.id.progressBar);

        ImageButton btnBack = findViewById(R.id.buttonBack);
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());
    }

    // ─────────────────────────────────────────────────────────
    //  Load profile
    // ─────────────────────────────────────────────────────────

    private void loadCurrentProfile() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) { finish(); return; }

        // Email — always from Firebase Auth
        textEmail.setText(user.getEmail() != null ? user.getEmail() : "");

        // Display name — check Firestore first, fall back to Auth profile
        db.collection("users").document(user.getUid()).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists() && doc.getString("fullName") != null) {
                        editTextName.setText(doc.getString("fullName"));
                    } else if (user.getDisplayName() != null) {
                        editTextName.setText(user.getDisplayName());
                    }
                })
                .addOnFailureListener(e -> {
                    // Fall back to Auth display name if Firestore fails
                    if (user.getDisplayName() != null) {
                        editTextName.setText(user.getDisplayName());
                    }
                });
    }

    // ─────────────────────────────────────────────────────────
    //  Click listeners
    // ─────────────────────────────────────────────────────────

    private void setupClickListeners() {
        buttonSaveName.setOnClickListener(v -> saveName());
        buttonChangePassword.setOnClickListener(v -> showChangePasswordDialog());
    }

    // ─────────────────────────────────────────────────────────
    //  Save name
    // ─────────────────────────────────────────────────────────

    private void saveName() {
        String newName = editTextName.getText().toString().trim();
        if (TextUtils.isEmpty(newName)) {
            Toast.makeText(this, "Name cannot be empty", Toast.LENGTH_SHORT).show();
            return;
        }

        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) return;

        showLoading(true);

        // 1. Update Firebase Auth display name
        UserProfileChangeRequest profileUpdates = new UserProfileChangeRequest.Builder()
                .setDisplayName(newName)
                .build();

        user.updateProfile(profileUpdates)
                .addOnCompleteListener(task -> {
                    // 2. Also save to Firestore users collection
                    Map<String, Object> data = new HashMap<>();
                    data.put("fullName", newName);
                    db.collection("users").document(user.getUid())
                            .set(data, com.google.firebase.firestore.SetOptions.merge())
                            .addOnSuccessListener(v -> {
                                showLoading(false);
                                Toast.makeText(this,
                                        "Name updated successfully", Toast.LENGTH_SHORT).show();
                            })
                            .addOnFailureListener(e -> {
                                showLoading(false);
                                Toast.makeText(this,
                                        "Failed to save name: " + e.getMessage(),
                                        Toast.LENGTH_LONG).show();
                            });
                });
    }

    // ─────────────────────────────────────────────────────────
    //  Change password dialog
    // ─────────────────────────────────────────────────────────

    private void showChangePasswordDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_change_password, null);

        EditText editCurrentPassword = dialogView.findViewById(R.id.editCurrentPassword);
        EditText editNewPassword     = dialogView.findViewById(R.id.editNewPassword);
        EditText editConfirmPassword = dialogView.findViewById(R.id.editConfirmPassword);

        new AlertDialog.Builder(this)
                .setTitle("Change Password")
                .setView(dialogView)
                .setPositiveButton("Update", (dialog, which) -> {
                    String current = editCurrentPassword.getText().toString().trim();
                    String newPass  = editNewPassword.getText().toString().trim();
                    String confirm  = editConfirmPassword.getText().toString().trim();
                    changePassword(current, newPass, confirm);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void changePassword(String current, String newPass, String confirm) {
        if (TextUtils.isEmpty(current) || TextUtils.isEmpty(newPass)
                || TextUtils.isEmpty(confirm)) {
            Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!newPass.equals(confirm)) {
            Toast.makeText(this, "New passwords do not match", Toast.LENGTH_SHORT).show();
            return;
        }
        if (newPass.length() < 6) {
            Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show();
            return;
        }

        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null || user.getEmail() == null) return;

        showLoading(true);

        // Re-authenticate first (required by Firebase before password change)
        AuthCredential credential =
                EmailAuthProvider.getCredential(user.getEmail(), current);

        user.reauthenticate(credential)
                .addOnSuccessListener(v -> {
                    // Re-auth succeeded — now update password
                    user.updatePassword(newPass)
                            .addOnSuccessListener(v2 -> {
                                showLoading(false);
                                Toast.makeText(this,
                                        "Password updated successfully",
                                        Toast.LENGTH_SHORT).show();
                            })
                            .addOnFailureListener(e -> {
                                showLoading(false);
                                Toast.makeText(this,
                                        "Failed to update password: " + e.getMessage(),
                                        Toast.LENGTH_LONG).show();
                            });
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Toast.makeText(this,
                            "Current password is incorrect",
                            Toast.LENGTH_LONG).show();
                });
    }

    // ─────────────────────────────────────────────────────────
    //  Loading state
    // ─────────────────────────────────────────────────────────

    private void showLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        buttonSaveName.setEnabled(!loading);
        buttonChangePassword.setEnabled(!loading);
    }
}