package com.example.ilearned;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

public class SummarizeNotesActivity extends AppCompatActivity {

    public static final String EXTRA_TEXT_TO_SUMMARIZE = "extra_text_to_summarize";
    public static final String EXTRA_SOURCE_LABEL      = "extra_source_label";

    // ── Views ─────────────────────────────────────────────────
    private EditText      editTextPaste;
    private Button        buttonSummarizeFromPaste;
    private Button        buttonChooseFile;
    private Button        buttonSummarizeDocument;   // grey → blue when file is ready
    private LinearLayout  layoutFileName;
    private TextView      textViewFileName;

    // ── State ─────────────────────────────────────────────────
    private String extractedDocumentText = null;
    private String documentFileName      = null;

    // ── File picker ───────────────────────────────────────────
    private final ActivityResultLauncher<String[]> documentPickerLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) processPickedDocument(uri);
            });

    // ─────────────────────────────────────────────────────────
    //  Lifecycle
    // ─────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_summarize_notes);
        initViews();
        setupClickListeners();
        // Start with summarize button greyed out — no file chosen yet
        setSummarizeButtonEnabled(false);
    }

    // ─────────────────────────────────────────────────────────
    //  View initialisation
    // ─────────────────────────────────────────────────────────

    private void initViews() {
        editTextPaste            = findViewById(R.id.editTextPaste);
        buttonSummarizeFromPaste = findViewById(R.id.buttonSummarizeFromPaste);
        buttonChooseFile         = findViewById(R.id.buttonUploadDocument);
        buttonSummarizeDocument  = findViewById(R.id.buttonSummarizeDocument);
        layoutFileName           = findViewById(R.id.layoutFileName);
        textViewFileName         = findViewById(R.id.textViewFileName);

        // Hide file name badge until a file is picked
        layoutFileName.setVisibility(View.GONE);

        // The summarize document button is always visible but starts disabled/grey
        buttonSummarizeDocument.setVisibility(View.VISIBLE);
    }

    // ─────────────────────────────────────────────────────────
    //  Click listeners
    // ─────────────────────────────────────────────────────────

    private void setupClickListeners() {

        // Back button
        ImageButton buttonBack = findViewById(R.id.buttonBack);
        if (buttonBack != null) {
            buttonBack.setOnClickListener(v -> finish());
        }

        // Summarize pasted text — works independently of the file section
        buttonSummarizeFromPaste.setOnClickListener(v -> {
            String pasted = editTextPaste.getText().toString().trim();
            if (TextUtils.isEmpty(pasted)) {
                Toast.makeText(this,
                        "Please paste or type some text first",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            returnResult(pasted, "Pasted notes");
        });

        // Choose file button — opens the file picker
        buttonChooseFile.setOnClickListener(v ->
                documentPickerLauncher.launch(new String[]{
                        "application/pdf",
                        "text/plain",
                        "application/msword",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                }));

        // Summarize document button — only active once a file has been loaded
        buttonSummarizeDocument.setOnClickListener(v -> {
            if (extractedDocumentText == null) {
                // Should not happen since button is disabled, but guard anyway
                Toast.makeText(this,
                        "Please choose a file first", Toast.LENGTH_SHORT).show();
                return;
            }
            // Return result to ChatbotFragment which will request the summary from Gemini
            returnResult(extractedDocumentText, documentFileName);
        });
    }

    // ─────────────────────────────────────────────────────────
    //  Document handling
    // ─────────────────────────────────────────────────────────

    private void processPickedDocument(Uri uri) {
        String text = DocumentTextExtractor.extractText(this, uri);

        if (TextUtils.isEmpty(text)) {
            Toast.makeText(this,
                    "Could not read text from this file.\nTry a .txt or .pdf file.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        // Store extracted content
        extractedDocumentText = text;
        documentFileName      = getFileName(uri);

        // Show file name badge
        textViewFileName.setText(documentFileName);
        layoutFileName.setVisibility(View.VISIBLE);

        // Activate the summarize button — turns it blue
        setSummarizeButtonEnabled(true);

        Toast.makeText(this,
                "File loaded! Tap \"Summarize Document\" to continue.",
                Toast.LENGTH_SHORT).show();
    }

    // ─────────────────────────────────────────────────────────
    //  Button state helper
    // ─────────────────────────────────────────────────────────


    private void setSummarizeButtonEnabled(boolean enabled) {
        buttonSummarizeDocument.setEnabled(enabled);
        TextView textViewHint = findViewById(R.id.textViewHint);
        if (enabled) {
            // Blue — file is ready, button is clickable
            buttonSummarizeDocument.setBackgroundTintList(
                    ContextCompat.getColorStateList(this, R.color.accent));
            buttonSummarizeDocument.setAlpha(1.0f);
            if (textViewHint != null) {
                textViewHint.setText("Tap the button to get your summary in the chat");
                textViewHint.setTextColor(ContextCompat.getColor(this, R.color.accent));
            }
        } else {
            // Grey — no file chosen yet
            buttonSummarizeDocument.setBackgroundTintList(
                    ContextCompat.getColorStateList(this, R.color.buttonDisabled));
            buttonSummarizeDocument.setAlpha(0.6f);
            if (textViewHint != null) {
                textViewHint.setText("Choose a file above to enable this button");
                textViewHint.setTextColor(ContextCompat.getColor(this, R.color.textSecondary));
            }
        }
    }

    // ─────────────────────────────────────────────────────────
    //  Return result to ChatbotFragment
    // ─────────────────────────────────────────────────────────

    private void returnResult(String text, String label) {
        Intent result = new Intent();
        result.putExtra(EXTRA_TEXT_TO_SUMMARIZE, text);
        result.putExtra(EXTRA_SOURCE_LABEL, label);
        setResult(RESULT_OK, result);
        finish(); // closes this Activity and returns to ChatbotFragment
    }

    // ─────────────────────────────────────────────────────────
    //  Helpers
    // ─────────────────────────────────────────────────────────

    private String getFileName(Uri uri) {
        String path = uri.getLastPathSegment();
        return path != null ? path : "document";
    }
}