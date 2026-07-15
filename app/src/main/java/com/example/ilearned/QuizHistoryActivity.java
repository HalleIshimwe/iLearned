package com.example.ilearned;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * QuizHistoryActivity
 *
 * Shows all documents the student has uploaded for quiz generation.
 * Each document row has a dropdown arrow.
 * Tapping a document row expands it to show up to 3 quiz type rows
 * (Multiple Choice / Application / Essay) with their last result score.
 * Tapping a quiz row loads that quiz and opens QuizScreenActivity.
 */
public class QuizHistoryActivity extends AppCompatActivity {

    private RecyclerView   recyclerView;
    private ProgressBar    progressBar;
    private TextView       textEmpty;
    private QuizStorageHelper storageHelper;
    private HistoryAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_quiz_history);

        storageHelper = new QuizStorageHelper();

        setupToolbar();
        initViews();
        loadHistory();
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Quiz History");
        }
    }

    @Override
    public boolean onOptionsItemSelected(android.view.MenuItem item) {
        if (item.getItemId() == android.R.id.home) { finish(); return true; }
        return super.onOptionsItemSelected(item);
    }

    private void initViews() {
        recyclerView = findViewById(R.id.recyclerViewHistory);
        progressBar  = findViewById(R.id.progressBar);
        textEmpty    = findViewById(R.id.textEmpty);

        adapter = new HistoryAdapter(new ArrayList<>(), this::onQuizSelected);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
    }

    private void loadHistory() {
        progressBar.setVisibility(View.VISIBLE);
        textEmpty.setVisibility(View.GONE);

        storageHelper.loadGroupedHistory(new QuizStorageHelper.GroupedHistoryCallback() {
            @Override
            public void onSuccess(List<QuizStorageHelper.DocumentHistory> documents) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    if (documents.isEmpty()) {
                        textEmpty.setVisibility(View.VISIBLE);
                    } else {
                        adapter.setData(documents);
                    }
                });
            }
            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(QuizHistoryActivity.this,
                            "Could not load history: " + error,
                            Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    // Called when a quiz row in the history list is tapped
    private void onQuizSelected(QuizStorageHelper.SavedQuizMeta meta) {
        progressBar.setVisibility(View.VISIBLE);

        storageHelper.loadQuiz(meta.id, new QuizStorageHelper.LoadCallback() {
            @Override
            public void onSuccess(List<QuizQuestion> questions, List<String> topics) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    if (questions.isEmpty()) {
                        Toast.makeText(QuizHistoryActivity.this,
                                "No questions found in this quiz.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    QuizQuestion.QuizType type = QuizQuestion.QuizType.valueOf(
                            meta.type != null ? meta.type : "MULTIPLE_CHOICE");

                    Intent intent = new Intent(QuizHistoryActivity.this, QuizScreenActivity.class);
                    intent.putExtra(QuizScreenActivity.EXTRA_QUIZ_TYPE, type.name());
                    intent.putExtra(QuizScreenActivity.EXTRA_FILE_NAME, meta.fileName);
                    intent.putExtra(QuizScreenActivity.EXTRA_QUIZ_ID,   meta.id);
                    intent.putExtra(QuizScreenActivity.EXTRA_QUESTIONS,  new ArrayList<>(questions));
                    intent.putStringArrayListExtra(QuizScreenActivity.EXTRA_TOPICS,
                            new ArrayList<>(topics));
                    startActivity(intent);
                });
            }
            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(QuizHistoryActivity.this,
                            "Failed to load quiz: " + error, Toast.LENGTH_LONG).show();
                });
            }
        });
    }
}