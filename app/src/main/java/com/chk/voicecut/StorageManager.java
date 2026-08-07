package com.chk.voicecut;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public final class StorageManager {
    private StorageManager() {}

    public static Uri saveMp3(Context context, File sourceMp3, String profile, String filename) throws IOException {
        String safeProfile = profile.substring(0, 1).toUpperCase() + profile.substring(1).toLowerCase();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Audio.Media.DISPLAY_NAME, filename);
            values.put(MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg");
            values.put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/VoiceCut/" + safeProfile + "/");
            values.put(MediaStore.Audio.Media.IS_PENDING, 1);
            ContentResolver resolver = context.getContentResolver();
            Uri uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values);
            if (uri == null) throw new IOException("Impossible de créer le fichier MP3 dans Music/VoiceCut");
            boolean success = false;
            try (OutputStream out = resolver.openOutputStream(uri); InputStream in = new FileInputStream(sourceMp3)) {
                if (out == null) throw new IOException("Flux de sortie MediaStore indisponible");
                copy(in, out);
                success = true;
            } finally {
                if (!success) resolver.delete(uri, null, null);
            }
            ContentValues ready = new ContentValues();
            ready.put(MediaStore.Audio.Media.IS_PENDING, 0);
            resolver.update(uri, ready, null, null);
            return uri;
        }

        File root = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC);
        File dir = new File(root, "VoiceCut/" + safeProfile);
        if (!dir.exists() && !dir.mkdirs()) throw new IOException("Impossible de créer " + dir);
        File dest = uniqueFile(dir, filename);
        try (InputStream in = new FileInputStream(sourceMp3); OutputStream out = new FileOutputStream(dest)) { copy(in, out); }
        return Uri.fromFile(dest);
    }

    public static boolean deleteOutput(Context context, Recording recording) {
        try {
            Uri uri = Uri.parse(recording.uri);
            if ("content".equals(uri.getScheme())) context.getContentResolver().delete(uri, null, null);
            else if ("file".equals(uri.getScheme())) { File f = new File(uri.getPath()); if (f.exists()) f.delete(); }
            return true;
        } catch (Exception e) { return false; }
    }

    public static boolean delete(Context context, Recording recording) {
        try {
            deleteOutput(context, recording);
            if (recording.sourceWavPath != null && !recording.sourceWavPath.isEmpty()) {
                File source = new File(recording.sourceWavPath);
                if (source.exists()) source.delete();
            }
            return true;
        } catch (Exception e) { return false; }
    }

    public static Uri rename(Context context, Recording recording, String newFilename) throws IOException {
        Uri uri = Uri.parse(recording.uri);
        if ("content".equals(uri.getScheme())) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Audio.Media.DISPLAY_NAME, newFilename);
            int updated = context.getContentResolver().update(uri, values, null, null);
            if (updated <= 0) throw new IOException("Renommage refusé par Android");
            return uri;
        }
        File old = new File(uri.getPath());
        File renamed = new File(old.getParentFile(), newFilename);
        if (!old.renameTo(renamed)) throw new IOException("Impossible de renommer le fichier");
        return Uri.fromFile(renamed);
    }

    public static Uri shareUri(Context context, Recording recording) {
        Uri uri = Uri.parse(recording.uri);
        if ("content".equals(uri.getScheme())) return uri;
        File f = new File(uri.getPath());
        return FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", f);
    }

    public static void copyFile(File source, File dest) throws IOException {
        File parent = dest.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) throw new IOException("Impossible de créer " + parent);
        try (InputStream in = new FileInputStream(source); OutputStream out = new FileOutputStream(dest)) { copy(in, out); }
    }

    private static File uniqueFile(File dir, String filename) {
        File result = new File(dir, filename);
        if (!result.exists()) return result;
        String base = filename.toLowerCase().endsWith(".mp3") ? filename.substring(0, filename.length() - 4) : filename;
        for (int i = 2; i < 1000; i++) {
            File f = new File(dir, base + "_" + i + ".mp3");
            if (!f.exists()) return f;
        }
        return new File(dir, base + "_" + System.currentTimeMillis() + ".mp3");
    }

    private static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[32 * 1024];
        int n;
        while ((n = in.read(buffer)) >= 0) if (n > 0) out.write(buffer, 0, n);
    }
}
