package com.chk.voicecut;

import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HistoryActivity extends AppCompatActivity {
    private LinearLayout container;
    private TextView empty;
    private RecordingStore store;
    private MediaPlayer player;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);
        container = findViewById(R.id.historyContainer);
        empty = findViewById(R.id.textEmpty);
        store = new RecordingStore(this);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        render();
    }

    @Override protected void onResume() { super.onResume(); if (store != null) render(); }
    @Override protected void onDestroy() { releasePlayer(); super.onDestroy(); }

    private void render() {
        container.removeAllViews();
        List<Recording> recordings = store.getAll();
        empty.setVisibility(recordings.isEmpty() ? View.VISIBLE : View.GONE);
        for (Recording r : recordings) container.addView(createCard(r));
    }

    private View createCard(Recording r) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackgroundResource(R.drawable.bg_panel);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.bottomMargin = dp(10);
        card.setLayoutParams(cardParams);

        TextView title = new TextView(this);
        title.setText(r.name);
        title.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
        title.setTextSize(18f);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        card.addView(title);

        TextView meta = new TextView(this);
        String date = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date(r.createdAt));
        meta.setText(String.format(Locale.FRANCE, "%s • %.3f s • %s", r.profile, r.durationMs / 1000.0, date));
        meta.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        meta.setPadding(0, dp(4), 0, dp(8));
        card.addView(meta);

        LinearLayout row1 = buttonRow();
        Button listen = actionButton("▶ Écouter"), modify = actionButton("Modifier"), rename = actionButton("Renommer");
        row1.addView(listen, weightParams());
        row1.addView(modify, weightParamsWithMargin());
        row1.addView(rename, weightParamsWithMargin());
        card.addView(row1);

        LinearLayout row2 = buttonRow();
        row2.setPadding(0, dp(6), 0, 0);
        Button share = actionButton("Partager"), delete = actionButton("Supprimer");
        row2.addView(share, weightParams());
        row2.addView(delete, weightParamsWithMargin());
        card.addView(row2);

        listen.setOnClickListener(v -> play(r));
        modify.setOnClickListener(v -> { Intent i = new Intent(this, MainActivity.class); i.putExtra(MainActivity.EXTRA_RECORDING_ID, r.id); startActivity(i); });
        rename.setOnClickListener(v -> rename(r));
        share.setOnClickListener(v -> share(r));
        delete.setOnClickListener(v -> delete(r));
        return card;
    }

    private void play(Recording r) {
        releasePlayer();
        try {
            player = new MediaPlayer();
            player.setDataSource(this, Uri.parse(r.uri));
            player.setOnPreparedListener(MediaPlayer::start);
            player.setOnCompletionListener(mp -> releasePlayer());
            player.prepareAsync();
        } catch (Exception e) { Toast.makeText(this, "Lecture impossible : " + e.getMessage(), Toast.LENGTH_LONG).show(); }
    }

    private void rename(Recording r) {
        EditText input = new EditText(this);
        String base = r.name.toLowerCase(Locale.ROOT).endsWith(".mp3") ? r.name.substring(0, r.name.length() - 4) : r.name;
        input.setText(base);
        input.setSelectAllOnFocus(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        new AlertDialog.Builder(this)
                .setTitle("Renommer")
                .setView(input)
                .setNegativeButton("Annuler", null)
                .setPositiveButton("Enregistrer", (d, w) -> {
                    String newName = RecordingStore.sanitizeBase(input.getText().toString()) + ".mp3";
                    try {
                        Uri newUri = StorageManager.rename(this, r, newName);
                        r.name = newName; r.uri = newUri.toString(); store.upsert(r); render();
                    } catch (Exception e) { Toast.makeText(this, "Renommage impossible : " + e.getMessage(), Toast.LENGTH_LONG).show(); }
                }).show();
    }

    private void share(Recording r) {
        try {
            Uri uri = StorageManager.shareUri(this, r);
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("audio/mpeg");
            share.putExtra(Intent.EXTRA_STREAM, uri);
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(share, "Partager " + r.name));
        } catch (Exception e) { Toast.makeText(this, "Partage impossible : " + e.getMessage(), Toast.LENGTH_LONG).show(); }
    }

    private void delete(Recording r) {
        new AlertDialog.Builder(this)
                .setTitle("Supprimer " + r.name + " ?")
                .setMessage("Le MP3 et sa source d'édition seront supprimés.")
                .setNegativeButton("Annuler", null)
                .setPositiveButton("Supprimer", (d, w) -> { StorageManager.delete(this, r); store.delete(r.id); render(); })
                .show();
    }

    private LinearLayout buttonRow() { LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); row.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)); return row; }
    private Button actionButton(String text) { Button b = new Button(this); b.setText(text); b.setTextAllCaps(false); b.setTextColor(ContextCompat.getColor(this, R.color.text_primary)); b.setTextSize(12f); b.setBackgroundResource(R.drawable.bg_button_dark); return b; }
    private LinearLayout.LayoutParams weightParams() { return new LinearLayout.LayoutParams(0, dp(48), 1f); }
    private LinearLayout.LayoutParams weightParamsWithMargin() { LinearLayout.LayoutParams p = weightParams(); p.leftMargin = dp(5); return p; }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void releasePlayer() { if (player != null) { try { player.stop(); } catch (Exception ignored) {} player.release(); player = null; } }
}
