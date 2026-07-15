package com.example.ilearned;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.google.firebase.auth.FirebaseAuth;

/**
 * SettingsFragment
 *
 * Displays four setting cards matching the reference screenshot:
 *  1. Profile         → ProfileActivity (edit name, view email, change password)
 *  2. Study Goals     → StudyGoalsActivity (placeholder)
 *  3. Notification Preferences → NotificationPrefsActivity (deadline alerts)
 *  4. Theme           → Light / Dark toggle (saved via SharedPreferences)
 *  5. Sign Out        → confirmation dialog then LoginActivity
 */
public class SettingsFragment extends Fragment {

    private Button buttonLight, buttonDark;
    private LinearLayout cardProfile, cardStudyGoals, cardNotifications;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initViews(view);
        setupThemeButtons();
        setupCardListeners();
        setupSignOut(view);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        buttonLight       = null;
        buttonDark        = null;
        cardProfile       = null;
        cardStudyGoals    = null;
        cardNotifications = null;
    }

    // ─────────────────────────────────────────────────────────
    //  Views
    // ─────────────────────────────────────────────────────────

    private void initViews(View view) {
        cardProfile       = view.findViewById(R.id.cardProfile);
        cardStudyGoals    = view.findViewById(R.id.cardStudyGoals);
        cardNotifications = view.findViewById(R.id.cardNotifications);
        buttonLight       = view.findViewById(R.id.buttonLight);
        buttonDark        = view.findViewById(R.id.buttonDark);
    }

    // ─────────────────────────────────────────────────────────
    //  Theme toggle
    // ─────────────────────────────────────────────────────────

    private void setupThemeButtons() {
        // Reflect current saved preference on the buttons
        boolean isDark = ThemeHelper.isDarkMode(requireContext());
        updateThemeButtonStyles(isDark);

        buttonLight.setOnClickListener(v -> {
            ThemeHelper.setTheme(requireContext(), false);
            updateThemeButtonStyles(false);
            // Recreate the host activity so the whole app reflects the new theme
            requireActivity().recreate();
        });

        buttonDark.setOnClickListener(v -> {
            ThemeHelper.setTheme(requireContext(), true);
            updateThemeButtonStyles(true);
            requireActivity().recreate();
        });
    }

    /**
     * Visually highlights the active theme button (filled/dark)
     * and dims the inactive one.
     */
    private void updateThemeButtonStyles(boolean isDark) {
        if (buttonLight == null || buttonDark == null) return;

        if (isDark) {
            // Dark is active
            buttonDark.setBackgroundTintList(
                    androidx.core.content.ContextCompat.getColorStateList(
                            requireContext(), R.color.textPrimary));
            buttonDark.setTextColor(
                    androidx.core.content.ContextCompat.getColor(
                            requireContext(), R.color.surface));
            buttonLight.setBackgroundTintList(
                    androidx.core.content.ContextCompat.getColorStateList(
                            requireContext(), R.color.surface));
            buttonLight.setTextColor(
                    androidx.core.content.ContextCompat.getColor(
                            requireContext(), R.color.textPrimary));
        } else {
            // Light is active
            buttonLight.setBackgroundTintList(
                    androidx.core.content.ContextCompat.getColorStateList(
                            requireContext(), R.color.textPrimary));
            buttonLight.setTextColor(
                    androidx.core.content.ContextCompat.getColor(
                            requireContext(), R.color.surface));
            buttonDark.setBackgroundTintList(
                    androidx.core.content.ContextCompat.getColorStateList(
                            requireContext(), R.color.surface));
            buttonDark.setTextColor(
                    androidx.core.content.ContextCompat.getColor(
                            requireContext(), R.color.textPrimary));
        }
    }

    // ─────────────────────────────────────────────────────────
    //  Card navigation
    // ─────────────────────────────────────────────────────────

    private void setupCardListeners() {
        cardProfile.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), ProfileActivity.class)));

        cardStudyGoals.setOnClickListener(v -> {
            // 1. Create the new fragment instance
            TimerFragment timerFragment = new TimerFragment();

            // 2. Begin transaction
            getParentFragmentManager().beginTransaction()
                    .replace(R.id.frameLayout, timerFragment) // Use your actual container ID
                    .addToBackStack(null) // Optional: Adds to backstack so back button works
                    .commit();
        });

        cardNotifications.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), NotificationPrefsActivity.class)));
    }

    // ─────────────────────────────────────────────────────────
    //  Sign out
    // ─────────────────────────────────────────────────────────

    private void setupSignOut(View view) {
        Button buttonSignOut = view.findViewById(R.id.buttonSignOut);
        if (buttonSignOut != null) {
            buttonSignOut.setOnClickListener(v -> showSignOutDialog());
        }
    }

    private void showSignOutDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle("Sign Out")
                .setMessage("Are you sure you want to sign out?")
                .setPositiveButton("Sign Out", (dialog, which) -> {
                    // Clear saved quiz session state
                    QuizSessionManager.get().clearSession(null);
                    ChatbotFragment.clearSavedSession();
                    FirebaseAuth.getInstance().signOut();
                    Intent intent = new Intent(requireContext(), LoginActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}