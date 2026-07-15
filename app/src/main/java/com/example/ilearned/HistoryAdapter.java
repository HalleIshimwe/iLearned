package com.example.ilearned;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

/**
 * HistoryAdapter
 *
 * Each row represents a document the student uploaded.
 * Tapping a row expands / collapses a panel showing up to 3 quiz
 * type sub-rows. Each sub-row shows:
 *   - Quiz type label ("Multiple Choice Quiz" etc.)
 *   - Score preview ("Last score: 14/20 (70%)" or "Not attempted yet")
 * Tapping a sub-row calls onQuizSelected to open QuizScreenActivity.
 */
public class HistoryAdapter
        extends RecyclerView.Adapter<HistoryAdapter.DocumentViewHolder> {

    public interface OnQuizSelectedListener {
        void onQuizSelected(QuizStorageHelper.SavedQuizMeta meta);
    }

    private List<QuizStorageHelper.DocumentHistory> data;
    private final OnQuizSelectedListener listener;

    public HistoryAdapter(List<QuizStorageHelper.DocumentHistory> data,
                          OnQuizSelectedListener listener) {
        this.data     = data;
        this.listener = listener;
    }

    public void setData(List<QuizStorageHelper.DocumentHistory> data) {
        this.data = data;
        notifyDataSetChanged();
    }

    // ── RecyclerView ──────────────────────────────────────────

    @NonNull
    @Override
    public DocumentViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_history_document, parent, false);
        return new DocumentViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull DocumentViewHolder holder, int position) {
        holder.bind(data.get(position), listener);
    }

    @Override
    public int getItemCount() {
        return data != null ? data.size() : 0;
    }

    // ── ViewHolder ────────────────────────────────────────────

    static class DocumentViewHolder extends RecyclerView.ViewHolder {

        private final TextView     textFileName;
        private final ImageView    imageArrow;
        private final LinearLayout layoutQuizList;

        DocumentViewHolder(View v) {
            super(v);
            textFileName   = v.findViewById(R.id.textFileName);
            imageArrow     = v.findViewById(R.id.imageArrow);
            layoutQuizList = v.findViewById(R.id.layoutQuizList);
        }

        void bind(QuizStorageHelper.DocumentHistory doc, OnQuizSelectedListener listener) {
            textFileName.setText(doc.fileName);

            // Build the quiz sub-rows from scratch each bind
            layoutQuizList.removeAllViews();
            for (QuizStorageHelper.SavedQuizMeta quiz : doc.quizzes) {
                View row = buildQuizRow(itemView.getContext(), quiz, listener);
                layoutQuizList.addView(row);
            }

            // Show / hide quiz list based on expanded state
            layoutQuizList.setVisibility(doc.expanded ? View.VISIBLE : View.GONE);
            imageArrow.setRotation(doc.expanded ? 180f : 0f);

            // Toggle expand/collapse on the document row
            itemView.setOnClickListener(v -> {
                doc.expanded = !doc.expanded;
                layoutQuizList.setVisibility(doc.expanded ? View.VISIBLE : View.GONE);
                imageArrow.animate().rotation(doc.expanded ? 180f : 0f).setDuration(200).start();
            });
        }

        private View buildQuizRow(android.content.Context ctx,
                                  QuizStorageHelper.SavedQuizMeta quiz,
                                  OnQuizSelectedListener listener) {
            // Inflate the quiz sub-row layout
            View row = LayoutInflater.from(ctx)
                    .inflate(R.layout.item_history_quiz, null);

            TextView textType   = row.findViewById(R.id.textQuizType);
            TextView textScore  = row.findViewById(R.id.textLastScore);
            TextView textArrow  = row.findViewById(R.id.textOpenArrow);

            textType.setText(quiz.getDisplayType());

            if (quiz.hasBeenAttempted()) {
                if ("MULTIPLE_CHOICE".equals(quiz.type)) {
                    textScore.setText("Last score: " + quiz.lastScore + "/" + quiz.lastTotal
                            + " (" + quiz.lastPercent + "%)");
                    textScore.setTextColor(
                            scoreColor(ctx, quiz.lastPercent));
                } else {
                    textScore.setText("Completed");
                    textScore.setTextColor(
                            ctx.getResources().getColor(R.color.quizCorrectGreen, null));
                }
            } else {
                textScore.setText("Not attempted yet");
                textScore.setTextColor(
                        ctx.getResources().getColor(R.color.textSecondary, null));
            }

            row.setOnClickListener(v -> listener.onQuizSelected(quiz));
            return row;
        }

        private int scoreColor(android.content.Context ctx, int percent) {
            if (percent >= 70)
                return ctx.getResources().getColor(R.color.quizCorrectGreen, null);
            else if (percent >= 40)
                return ctx.getResources().getColor(R.color.accent, null);
            else
                return ctx.getResources().getColor(R.color.quizWrongRed, null);
        }
    }
}