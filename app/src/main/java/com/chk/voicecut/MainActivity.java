package com.chk.voicecut;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements AudioRecorderManager.Listener {
    public static final String EXTRA_RECORDING_ID = "recording_id";
    private static final int REQ_MIC = 101;
    private static final int REQ_STORAGE = 102;
    private static final String[] PROFILES = {"CHEIKH", "YVANE", "NELVYN"};
    private static final String[] CATEGORY_LABELS = {"Bonjour", "Attaque", "Douleur", "Victoire", "Coffre trouvé", "Arrivée île", "Ennemi repéré", "Embarquement"};
    private static final String[] CATEGORY_SLUGS = {"bonjour", "attaque", "douleur", "victoire", "coffre_trouve", "arrivee_ile", "ennemi_repere", "embarquement"};
    private static final String[] BITRATES = {"96 kb/s", "128 kb/s — recommandé", "192 kb/s", "256 kb/s"};

    private Spinner spinnerProfile, spinnerCategory, spinnerBitrate;
    private EditText editFilename;
    private Switch switchRapid;
    private Button btnRecord, btnStop, btnExport, btnPreviewResult;
    private ProgressBar progressMic;
    private TextView textTimer, textLevel, textStatus, textStart, textEnd, textSelectionDuration, textDetectionInfo, textPreMargin, textPostMargin, textPreviewRequirement;
    private SeekBar seekPreMargin, seekPostMargin;
    private CheckBox checkNoise, checkNormalize, checkSmartGain;
    private LinearLayout editorPanel;
    private WaveformView waveform;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private AudioRecorderManager recorder;
    private RecordingStore store;
    private WavFile.AudioData audioData;
    private File workingWav;
    private MediaPlayer player;
    private long selectionStartMs, selectionEndMs;
    private boolean previewApproved, pendingRecord, pendingExport;
    private Recording editingRecording;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        bindViews();
        store = new RecordingStore(this);
        recorder = new AudioRecorderManager(this, this);
        setupSpinners();
        setupListeners();
        refreshFilenameSuggestion();
        String id = getIntent().getStringExtra(EXTRA_RECORDING_ID);
        if (id != null && !id.isEmpty()) loadExistingRecording(id);
    }

    private void bindViews() {
        spinnerProfile = findViewById(R.id.spinnerProfile); spinnerCategory = findViewById(R.id.spinnerCategory); spinnerBitrate = findViewById(R.id.spinnerBitrate);
        editFilename = findViewById(R.id.editFilename); switchRapid = findViewById(R.id.switchRapidMode);
        btnRecord = findViewById(R.id.btnRecord); btnStop = findViewById(R.id.btnStop); btnExport = findViewById(R.id.btnExport); btnPreviewResult = findViewById(R.id.btnPreviewResult);
        progressMic = findViewById(R.id.progressMic); textTimer = findViewById(R.id.textTimer); textLevel = findViewById(R.id.textLevel); textStatus = findViewById(R.id.textStatus);
        textStart = findViewById(R.id.textStart); textEnd = findViewById(R.id.textEnd); textSelectionDuration = findViewById(R.id.textSelectionDuration);
        textDetectionInfo = findViewById(R.id.textDetectionInfo); textPreMargin = findViewById(R.id.textPreMargin); textPostMargin = findViewById(R.id.textPostMargin); textPreviewRequirement = findViewById(R.id.textPreviewRequirement);
        seekPreMargin = findViewById(R.id.seekPreMargin); seekPostMargin = findViewById(R.id.seekPostMargin);
        checkNoise = findViewById(R.id.checkNoiseReduction); checkNormalize = findViewById(R.id.checkNormalize); checkSmartGain = findViewById(R.id.checkSmartGain);
        editorPanel = findViewById(R.id.editorPanel); waveform = findViewById(R.id.waveform);
    }

    private void setupSpinners() {
        ArrayAdapter<String> p = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, PROFILES); p.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); spinnerProfile.setAdapter(p);
        ArrayAdapter<String> c = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, CATEGORY_LABELS); c.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); spinnerCategory.setAdapter(c);
        ArrayAdapter<String> b = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, BITRATES); b.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); spinnerBitrate.setAdapter(b); spinnerBitrate.setSelection(1);
    }

    private void setupListeners() {
        btnRecord.setOnClickListener(v -> startRecordingFlow());
        btnStop.setOnClickListener(v -> recorder.stop());
        findViewById(R.id.btnHistory).setOnClickListener(v -> startActivity(new Intent(this, HistoryActivity.class)));
        findViewById(R.id.btnAutoCut).setOnClickListener(v -> runAutoCut());
        findViewById(R.id.btnPlaySelection).setOnClickListener(v -> preview(false));
        btnPreviewResult.setOnClickListener(v -> preview(true));
        btnExport.setOnClickListener(v -> exportFlow());
        findViewById(R.id.btnAutoEnhance).setOnClickListener(v -> { checkNoise.setChecked(true); checkNormalize.setChecked(true); checkSmartGain.setChecked(true); invalidatePreview(); textStatus.setText("Amélioration automatique : réglages voix activés"); });
        bindAdjust(R.id.btnStartMinus100, true, -100); bindAdjust(R.id.btnStartMinus10, true, -10); bindAdjust(R.id.btnStartPlus10, true, 10); bindAdjust(R.id.btnStartPlus100, true, 100);
        bindAdjust(R.id.btnEndMinus100, false, -100); bindAdjust(R.id.btnEndMinus10, false, -10); bindAdjust(R.id.btnEndPlus10, false, 10); bindAdjust(R.id.btnEndPlus100, false, 100);
        findViewById(R.id.btnTrimBefore).setOnClickListener(v -> trimBefore()); findViewById(R.id.btnTrimAfter).setOnClickListener(v -> trimAfter());

        waveform.setSelectionListener(new WaveformView.SelectionListener() {
            @Override public void onSelectionChanged(long startMs, long endMs, boolean fromUser) { selectionStartMs = startMs; selectionEndMs = endMs; updateSelectionUi(); if (fromUser) invalidatePreview(); }
            @Override public void onSeek(long positionMs) { waveform.setPlayheadMs(positionMs); }
        });
        spinnerProfile.setOnItemSelectedListener(new SimpleItemSelectedListener(this::onNamingSelectionChanged));
        spinnerCategory.setOnItemSelectedListener(new SimpleItemSelectedListener(this::onNamingSelectionChanged));
        spinnerBitrate.setOnItemSelectedListener(new SimpleItemSelectedListener(this::invalidatePreview));
        seekPreMargin.setOnSeekBarChangeListener(simpleSeek(progress -> { textPreMargin.setText("Marge avant : " + progress + " ms"); invalidatePreview(); }));
        seekPostMargin.setOnSeekBarChangeListener(simpleSeek(progress -> { textPostMargin.setText("Marge après : " + progress + " ms"); invalidatePreview(); }));
        View.OnClickListener processingChanged = v -> invalidatePreview();
        checkNoise.setOnClickListener(processingChanged); checkNormalize.setOnClickListener(processingChanged); checkSmartGain.setOnClickListener(processingChanged);
    }

    private void onNamingSelectionChanged() { if (editingRecording == null) refreshFilenameSuggestion(); }
    private void refreshFilenameSuggestion() { if (store == null || spinnerProfile.getSelectedItemPosition() < 0 || spinnerCategory.getSelectedItemPosition() < 0) return; editFilename.setText(store.suggestFilename(selectedProfile(), selectedCategorySlug())); editFilename.setSelection(editFilename.getText().length()); }

    private void startRecordingFlow() {
        releasePlayer();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) { pendingRecord = true; ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC); return; }
        startRecordingNow();
    }

    private void startRecordingNow() {
        cleanupWorkingIfTemporary(); editingRecording = null; audioData = null; editorPanel.setVisibility(View.GONE); previewApproved = false; btnExport.setEnabled(false); textPreviewRequirement.setText("Écoute complète obligatoire avant l'export.");
        try {
            workingWav = new File(getCacheDir(), "voicecut_take_" + System.currentTimeMillis() + ".wav");
            recorder.start(workingWav); btnRecord.setEnabled(false); btnStop.setEnabled(true); spinnerProfile.setEnabled(false); spinnerCategory.setEnabled(false); textStatus.setText("Enregistrement PCM en cours…");
        } catch (Exception e) { showError("Démarrage impossible", e); }
    }

    @Override public void onLevel(double db, long elapsedMs) { runOnUiThread(() -> { textTimer.setText(formatDuration(elapsedMs)); textLevel.setText(String.format(Locale.FRANCE, "Micro : %.1f dB", db)); int level = (int) Math.round((Math.max(-60.0, Math.min(0.0, db)) + 60.0) / 60.0 * 100.0); progressMic.setProgress(level); }); }
    @Override public void onStopped(File wavFile, int sampleRate) { runOnUiThread(() -> { btnRecord.setEnabled(true); btnStop.setEnabled(false); spinnerProfile.setEnabled(true); spinnerCategory.setEnabled(true); progressMic.setProgress(0); textStatus.setText("Analyse de la voix…"); loadWorkingWav(true, -1, -1); }); }
    @Override public void onError(Exception error) { runOnUiThread(() -> { btnRecord.setEnabled(true); btnStop.setEnabled(false); spinnerProfile.setEnabled(true); spinnerCategory.setEnabled(true); showError("Erreur audio", error); }); }

    private void loadWorkingWav(boolean autoDetect, long preferredStart, long preferredEnd) {
        final File wav = workingWav; final int pre = seekPreMargin.getProgress(); final int post = seekPostMargin.getProgress();
        executor.execute(() -> {
            try {
                WavFile.AudioData data = WavFile.read(wav);
                VoiceActivityDetector.Result detection = autoDetect ? VoiceActivityDetector.detect(data.samples, data.sampleRate, pre, post) : null;
                runOnUiThread(() -> {
                    audioData = data; waveform.setAudio(data.samples, data.sampleRate); editorPanel.setVisibility(View.VISIBLE);
                    if (detection != null) { selectionStartMs = detection.startMs(data.sampleRate); selectionEndMs = detection.endMs(data.sampleRate); textDetectionInfo.setText(String.format(Locale.FRANCE, "Bruit %.1f dB • seuil voix %.1f dB", detection.noiseFloorDb, detection.thresholdDb)); }
                    else { selectionStartMs = Math.max(0, preferredStart); selectionEndMs = preferredEnd > 0 ? Math.min(data.durationMs(), preferredEnd) : data.durationMs(); }
                    waveform.setSelection(selectionStartMs, selectionEndMs); updateSelectionUi(); invalidatePreview(); textStatus.setText("Coupe prête — ajustez si nécessaire");
                });
            } catch (Exception e) { runOnUiThread(() -> showError("Impossible de lire le WAV", e)); }
        });
    }

    private void runAutoCut() {
        if (audioData == null) return;
        textStatus.setText("Détection adaptative de la voix…"); short[] samples = audioData.samples; int rate = audioData.sampleRate, pre = seekPreMargin.getProgress(), post = seekPostMargin.getProgress();
        executor.execute(() -> { VoiceActivityDetector.Result r = VoiceActivityDetector.detect(samples, rate, pre, post); runOnUiThread(() -> { selectionStartMs = r.startMs(rate); selectionEndMs = r.endMs(rate); waveform.setSelection(selectionStartMs, selectionEndMs); textDetectionInfo.setText(String.format(Locale.FRANCE, "Bruit %.1f dB • seuil voix %.1f dB", r.noiseFloorDb, r.thresholdDb)); updateSelectionUi(); invalidatePreview(); textStatus.setText("Voix recalée automatiquement"); }); });
    }

    private void bindAdjust(int id, boolean start, long deltaMs) {
        findViewById(id).setOnClickListener(v -> { if (audioData == null) return; long duration = audioData.durationMs(); if (start) selectionStartMs = clamp(selectionStartMs + deltaMs, 0, Math.max(0, selectionEndMs - 1)); else selectionEndMs = clamp(selectionEndMs + deltaMs, Math.min(duration, selectionStartMs + 1), duration); waveform.setSelection(selectionStartMs, selectionEndMs); updateSelectionUi(); invalidatePreview(); });
    }

    private void updateSelectionUi() { textStart.setText("Début : " + selectionStartMs + " ms"); textEnd.setText("Fin : " + selectionEndMs + " ms"); textSelectionDuration.setText("Sélection : " + Math.max(0, selectionEndMs - selectionStartMs) + " ms"); }

    private void trimBefore() {
        if (audioData == null || selectionStartMs <= 0) return;
        int from = (int) Math.min(audioData.samples.length, Math.round(selectionStartMs * audioData.sampleRate / 1000.0));
        short[] trimmed = Arrays.copyOfRange(audioData.samples, from, audioData.samples.length); long oldEnd = selectionEndMs; replaceWorkingAudio(trimmed, Math.max(0, oldEnd - selectionStartMs));
    }
    private void trimAfter() {
        if (audioData == null || selectionEndMs >= audioData.durationMs()) return;
        int to = (int) Math.max(1, Math.min(audioData.samples.length, Math.round(selectionEndMs * audioData.sampleRate / 1000.0)));
        short[] trimmed = Arrays.copyOfRange(audioData.samples, 0, to); replaceWorkingAudio(trimmed, Math.min(selectionEndMs, Math.round(trimmed.length * 1000.0 / audioData.sampleRate)));
    }

    private void replaceWorkingAudio(short[] samples, long newEndMs) {
        int rate = audioData.sampleRate; textStatus.setText("Application de la coupe…");
        executor.execute(() -> { try { WavFile.write(workingWav, rate, samples); runOnUiThread(() -> { audioData = new WavFile.AudioData(rate, samples); selectionStartMs = 0; selectionEndMs = Math.min(audioData.durationMs(), Math.max(1, newEndMs)); waveform.setAudio(samples, rate); waveform.setSelection(selectionStartMs, selectionEndMs); updateSelectionUi(); invalidatePreview(); textStatus.setText("Partie indésirable supprimée"); }); } catch (IOException e) { runOnUiThread(() -> showError("Coupe impossible", e)); } });
    }

    private void preview(boolean processed) {
        if (audioData == null || selectionEndMs <= selectionStartMs) return;
        releasePlayer(); previewApproved = false; btnExport.setEnabled(false); textPreviewRequirement.setText(processed ? "Écoute du résultat en cours…" : "Écoute brute de la sélection."); textStatus.setText("Préparation de l'écoute…");
        short[] source = audioData.samples; int rate = audioData.sampleRate; long start = selectionStartMs, end = selectionEndMs; boolean noise = checkNoise.isChecked(), normalize = checkNormalize.isChecked(), smart = checkSmartGain.isChecked();
        executor.execute(() -> { try { short[] segment = AudioProcessor.slice(source, rate, start, end); if (processed) segment = AudioProcessor.process(segment, rate, noise, normalize, smart); File preview = new File(getCacheDir(), processed ? "voicecut_preview_result.wav" : "voicecut_preview_raw.wav"); WavFile.write(preview, rate, segment); runOnUiThread(() -> playPreviewFile(preview, processed)); } catch (Exception e) { runOnUiThread(() -> showError("Préécoute impossible", e)); } });
    }

    private void playPreviewFile(File file, boolean processed) {
        try {
            player = new MediaPlayer(); player.setDataSource(file.getAbsolutePath());
            player.setOnPreparedListener(mp -> { mp.start(); textStatus.setText(processed ? "Écoutez le résultat final…" : "Lecture de la sélection…"); });
            player.setOnCompletionListener(mp -> { releasePlayer(); if (processed) { previewApproved = true; btnExport.setEnabled(true); textPreviewRequirement.setText("Résultat écouté : export autorisé."); textStatus.setText("Résultat validé pour l'export MP3"); } else textStatus.setText("Lecture terminée"); });
            player.prepareAsync();
        } catch (Exception e) { showError("Lecture impossible", e); }
    }

    private void exportFlow() {
        if (!previewApproved) { Toast.makeText(this, "Écoutez d'abord entièrement le résultat.", Toast.LENGTH_LONG).show(); return; }
        if (Build.VERSION.SDK_INT <= 28 && ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) { pendingExport = true; ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_STORAGE); return; }
        exportNow();
    }

    private void exportNow() {
        if (audioData == null) return;
        String base = editFilename.getText().toString().trim(); if (base.toLowerCase(Locale.ROOT).endsWith(".mp3")) base = base.substring(0, base.length() - 4); base = RecordingStore.sanitizeBase(base);
        final String filename = base + ".mp3", profile = selectedProfile(), category = selectedCategorySlug(); final int bitrate = selectedBitrate(); final WavFile.AudioData data = audioData;
        final long start = selectionStartMs, end = selectionEndMs; final boolean noise = checkNoise.isChecked(), normalize = checkNormalize.isChecked(), smart = checkSmartGain.isChecked(); final Recording previous = editingRecording;
        btnExport.setEnabled(false); btnPreviewResult.setEnabled(false); textStatus.setText("Encodage MP3 LAME…");
        executor.execute(() -> {
            File tempMp3 = new File(getCacheDir(), "voicecut_export_" + System.currentTimeMillis() + ".mp3");
            try {
                short[] segment = AudioProcessor.slice(data.samples, data.sampleRate, start, end); short[] processed = AudioProcessor.process(segment, data.sampleRate, noise, normalize, smart);
                Mp3Encoder.encodeMono(processed, data.sampleRate, bitrate, tempMp3);
                Uri saved = StorageManager.saveMp3(this, tempMp3, profile, filename);
                String id = previous != null ? previous.id : UUID.randomUUID().toString(); File archive = new File(new File(getFilesDir(), "sources"), id + ".wav"); StorageManager.copyFile(workingWav, archive);
                if (previous != null && previous.uri != null && !previous.uri.equals(saved.toString())) StorageManager.deleteOutput(this, previous);
                Recording r = previous != null ? previous : new Recording();
                r.id = id; r.name = filename; r.profile = profile; r.category = category; r.durationMs = Math.round(processed.length * 1000.0 / data.sampleRate); r.createdAt = System.currentTimeMillis(); r.uri = saved.toString(); r.sourceWavPath = archive.getAbsolutePath(); r.selectionStartMs = start; r.selectionEndMs = end; r.noiseReduction = noise; r.normalize = normalize; r.smartGain = smart; r.bitrateKbps = bitrate;
                store.upsert(r); tempMp3.delete(); runOnUiThread(() -> onExportSuccess(r));
            } catch (Exception e) { tempMp3.delete(); runOnUiThread(() -> { btnPreviewResult.setEnabled(true); btnExport.setEnabled(previewApproved); showError("Export MP3 impossible", e); }); }
        });
    }

    private void onExportSuccess(Recording r) {
        btnPreviewResult.setEnabled(true); textStatus.setText("MP3 enregistré : Music/VoiceCut/" + prettyProfile(r.profile) + "/" + r.name); Toast.makeText(this, "MP3 créé : " + r.name, Toast.LENGTH_LONG).show(); editingRecording = null;
        if (switchRapid.isChecked()) resetForNextTake(); else { previewApproved = false; btnExport.setEnabled(false); textPreviewRequirement.setText("Export terminé. Réécoutez après toute modification."); }
    }

    private void resetForNextTake() {
        releasePlayer(); audioData = null; editorPanel.setVisibility(View.GONE); previewApproved = false; btnExport.setEnabled(false); textTimer.setText("00:00.000"); textLevel.setText("Micro : -∞ dB"); progressMic.setProgress(0); cleanupWorkingIfTemporary(); workingWav = null; refreshFilenameSuggestion(); textStatus.setText("Prêt pour la prise suivante : " + editFilename.getText());
    }

    private void loadExistingRecording(String id) {
        Recording r = store.findById(id);
        if (r == null || r.sourceWavPath == null || r.sourceWavPath.isEmpty()) { Toast.makeText(this, "Source d'édition introuvable.", Toast.LENGTH_LONG).show(); return; }
        File source = new File(r.sourceWavPath); if (!source.exists()) { Toast.makeText(this, "Le WAV source a été supprimé.", Toast.LENGTH_LONG).show(); return; }
        editingRecording = r; spinnerProfile.setSelection(indexOf(PROFILES, r.profile)); spinnerCategory.setSelection(indexOf(CATEGORY_SLUGS, r.category)); spinnerBitrate.setSelection(bitrateIndex(r.bitrateKbps));
        String base = r.name.toLowerCase(Locale.ROOT).endsWith(".mp3") ? r.name.substring(0, r.name.length() - 4) : r.name; editFilename.setText(base); checkNoise.setChecked(r.noiseReduction); checkNormalize.setChecked(r.normalize); checkSmartGain.setChecked(r.smartGain); switchRapid.setChecked(false);
        workingWav = new File(getCacheDir(), "voicecut_edit_" + r.id + ".wav");
        executor.execute(() -> { try { StorageManager.copyFile(source, workingWav); runOnUiThread(() -> loadWorkingWav(false, r.selectionStartMs, r.selectionEndMs)); } catch (Exception e) { runOnUiThread(() -> showError("Chargement de l'édition impossible", e)); } });
    }

    private void invalidatePreview() { previewApproved = false; if (btnExport != null) btnExport.setEnabled(false); if (textPreviewRequirement != null) textPreviewRequirement.setText("Réécoutez le résultat après cette modification."); }
    private String selectedProfile() { return PROFILES[Math.max(0, spinnerProfile.getSelectedItemPosition())]; }
    private String selectedCategorySlug() { return CATEGORY_SLUGS[Math.max(0, spinnerCategory.getSelectedItemPosition())]; }
    private int selectedBitrate() { switch (spinnerBitrate.getSelectedItemPosition()) { case 0: return 96; case 2: return 192; case 3: return 256; default: return 128; } }
    private static int bitrateIndex(int bitrate) { if (bitrate == 96) return 0; if (bitrate == 192) return 2; if (bitrate == 256) return 3; return 1; }
    private static int indexOf(String[] values, String value) { if (value == null) return 0; for (int i = 0; i < values.length; i++) if (values[i].equalsIgnoreCase(value)) return i; return 0; }
    private String prettyProfile(String profile) { return profile.substring(0, 1).toUpperCase() + profile.substring(1).toLowerCase(Locale.ROOT); }
    private String formatDuration(long ms) { return String.format(Locale.FRANCE, "%02d:%02d.%03d", ms / 60000, (ms / 1000) % 60, ms % 1000); }

    private void showError(String title, Exception e) { textStatus.setText(title + " : " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage())); new AlertDialog.Builder(this).setTitle(title).setMessage(e.getMessage() == null ? e.toString() : e.getMessage()).setPositiveButton("OK", null).show(); }
    private void releasePlayer() { if (player != null) { try { player.stop(); } catch (Exception ignored) {} player.release(); player = null; } }
    private void cleanupWorkingIfTemporary() { if (workingWav != null && workingWav.exists() && workingWav.getParentFile() != null && workingWav.getParentFile().equals(getCacheDir())) workingWav.delete(); }

    @Override protected void onDestroy() { releasePlayer(); if (recorder != null) recorder.release(); cleanupWorkingIfTemporary(); executor.shutdownNow(); super.onDestroy(); }
    @Override public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults); boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
        if (requestCode == REQ_MIC && pendingRecord) { pendingRecord = false; if (granted) startRecordingNow(); else Toast.makeText(this, "La permission microphone est nécessaire.", Toast.LENGTH_LONG).show(); }
        else if (requestCode == REQ_STORAGE && pendingExport) { pendingExport = false; if (granted) exportNow(); else Toast.makeText(this, "Android 9 et antérieur nécessite l'autorisation pour écrire dans Music/VoiceCut.", Toast.LENGTH_LONG).show(); }
    }

    private static long clamp(long value, long min, long max) { return Math.max(min, Math.min(max, value)); }
    private static SeekBar.OnSeekBarChangeListener simpleSeek(ProgressConsumer consumer) { return new SeekBar.OnSeekBarChangeListener() { @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) { if (fromUser) consumer.accept(progress); } @Override public void onStartTrackingTouch(SeekBar seekBar) {} @Override public void onStopTrackingTouch(SeekBar seekBar) {} }; }
    private interface ProgressConsumer { void accept(int progress); }
    private static final class SimpleItemSelectedListener implements android.widget.AdapterView.OnItemSelectedListener {
        private final Runnable action; SimpleItemSelectedListener(Runnable action) { this.action = action; }
        @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) { action.run(); }
        @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
    }
}
