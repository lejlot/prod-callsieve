package com.countryguard.app;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public final class HistoryStore {
    private static final Object LOCK = new Object();
    private final SharedPreferences prefs;
    public HistoryStore(Context context) { prefs = context.getSharedPreferences("history", Context.MODE_PRIVATE); }
    public JSONArray read() {
        synchronized (LOCK) {
            try { return new JSONArray(prefs.getString("entries", "[]")); }
            catch (JSONException e) { return new JSONArray(); }
        }
    }
    public void add(String raw, RuleEngine.Decision decision) {
        add(raw, new ProtectionPolicy.Result(decision.blocked() ? ProtectionPolicy.Action.BLOCK : ProtectionPolicy.Action.ALLOW,
            decision.normalized(), decision.region(), decision.reason()));
    }
    public void add(String raw, ProtectionPolicy.Result decision) {
        synchronized (LOCK) {
            try {
                JSONObject entry = new JSONObject();
                entry.put("number", decision.normalized().isEmpty() ? (raw == null ? "Unknown" : raw) : decision.normalized());
                entry.put("blocked", decision.action() == ProtectionPolicy.Action.BLOCK);
                entry.put("silenced", decision.action() == ProtectionPolicy.Action.SILENCE);
                entry.put("region", decision.region());
                entry.put("reason", decision.reason());
                entry.put("time", System.currentTimeMillis());
                JSONArray next = new JSONArray().put(entry);
                JSONArray previous = read();
                for (int i = 0; i < Math.min(99, previous.length()); i++) next.put(previous.get(i));
                prefs.edit().putString("entries", next.toString()).apply();
            } catch (JSONException ignored) { }
        }
    }
    public void clear() { synchronized (LOCK) { prefs.edit().remove("entries").apply(); } }
}
