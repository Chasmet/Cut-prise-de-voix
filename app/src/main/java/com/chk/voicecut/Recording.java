package com.chk.voicecut;

import org.json.JSONException;
import org.json.JSONObject;

public class Recording {
    public String id;
    public String name;
    public String profile;
    public String category;
    public long durationMs;
    public long createdAt;
    public String uri;
    public String sourceWavPath;
    public long selectionStartMs;
    public long selectionEndMs;
    public boolean noiseReduction;
    public boolean normalize;
    public boolean smartGain;
    public int bitrateKbps;

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("name", name);
        o.put("profile", profile);
        o.put("category", category);
        o.put("durationMs", durationMs);
        o.put("createdAt", createdAt);
        o.put("uri", uri);
        o.put("sourceWavPath", sourceWavPath);
        o.put("selectionStartMs", selectionStartMs);
        o.put("selectionEndMs", selectionEndMs);
        o.put("noiseReduction", noiseReduction);
        o.put("normalize", normalize);
        o.put("smartGain", smartGain);
        o.put("bitrateKbps", bitrateKbps);
        return o;
    }

    public static Recording fromJson(JSONObject o) {
        Recording r = new Recording();
        r.id = o.optString("id", "");
        r.name = o.optString("name", "");
        r.profile = o.optString("profile", "CHEIKH");
        r.category = o.optString("category", "bonjour");
        r.durationMs = o.optLong("durationMs", 0L);
        r.createdAt = o.optLong("createdAt", 0L);
        r.uri = o.optString("uri", "");
        r.sourceWavPath = o.optString("sourceWavPath", "");
        r.selectionStartMs = o.optLong("selectionStartMs", 0L);
        r.selectionEndMs = o.optLong("selectionEndMs", r.durationMs);
        r.noiseReduction = o.optBoolean("noiseReduction", true);
        r.normalize = o.optBoolean("normalize", true);
        r.smartGain = o.optBoolean("smartGain", true);
        r.bitrateKbps = o.optInt("bitrateKbps", 128);
        return r;
    }
}
