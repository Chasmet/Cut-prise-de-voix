package com.chk.voicecut;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

public class RecordingStore {
    private static final String PREFS = "voicecut_store";
    private static final String KEY_RECORDINGS = "recordings";
    private final SharedPreferences prefs;

    public RecordingStore(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public synchronized List<Recording> getAll() {
        List<Recording> result = new ArrayList<>();
        String raw = prefs.getString(KEY_RECORDINGS, "[]");
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) result.add(Recording.fromJson(arr.getJSONObject(i)));
        } catch (JSONException ignored) {
        }
        Collections.sort(result, (a, b) -> Long.compare(b.createdAt, a.createdAt));
        return result;
    }

    public synchronized Recording findById(String id) {
        for (Recording r : getAll()) if (r.id.equals(id)) return r;
        return null;
    }

    public synchronized void upsert(Recording recording) {
        List<Recording> list = getAll();
        boolean replaced = false;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id.equals(recording.id)) {
                list.set(i, recording);
                replaced = true;
                break;
            }
        }
        if (!replaced) list.add(recording);
        save(list);
    }

    public synchronized void delete(String id) {
        List<Recording> list = getAll();
        Iterator<Recording> it = list.iterator();
        while (it.hasNext()) if (it.next().id.equals(id)) it.remove();
        save(list);
    }

    public synchronized String suggestFilename(String profile, String base) {
        String cleanBase = sanitizeBase(base);
        int max = 0;
        String prefix = cleanBase + "_";
        for (Recording r : getAll()) {
            if (!profile.equalsIgnoreCase(r.profile)) continue;
            String n = r.name;
            if (!n.toLowerCase(Locale.ROOT).endsWith(".mp3")) continue;
            String withoutExt = n.substring(0, n.length() - 4);
            if (!withoutExt.startsWith(prefix)) continue;
            String suffix = withoutExt.substring(prefix.length());
            try { max = Math.max(max, Integer.parseInt(suffix)); } catch (NumberFormatException ignored) {}
        }
        return String.format(Locale.ROOT, "%s_%02d", cleanBase, max + 1);
    }

    public static String sanitizeBase(String input) {
        String s = input == null ? "prise" : input.trim().toLowerCase(Locale.ROOT);
        s = s.replaceAll("[^a-z0-9_àâäéèêëîïôöùûüç-]+", "_");
        s = s.replaceAll("_+", "_");
        s = s.replaceAll("^_+|_+$", "");
        return s.isEmpty() ? "prise" : s;
    }

    private void save(List<Recording> list) {
        JSONArray arr = new JSONArray();
        for (Recording r : list) {
            try { arr.put(r.toJson()); } catch (JSONException ignored) {}
        }
        prefs.edit().putString(KEY_RECORDINGS, arr.toString()).apply();
    }
}
