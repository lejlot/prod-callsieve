package com.countryguard.app;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;

public final class AttemptStore {
    private static final Object LOCK = new Object();
    private final SharedPreferences prefs;
    public AttemptStore(Context context) { prefs = context.getSharedPreferences("attempts", Context.MODE_PRIVATE); }
    public int count(String number, int windowMinutes, boolean record) {
        if (number.isEmpty()) return 0;
        synchronized (LOCK) {
            long now = System.currentTimeMillis();
            try {
                JSONObject all = new JSONObject(prefs.getString("entries", "{}"));
                JSONObject next = new JSONObject(); int count = 0;
                java.util.Iterator<String> keys = all.keys();
                while (keys.hasNext()) {
                    String key = keys.next(); JSONArray old = all.optJSONArray(key); JSONArray fresh = new JSONArray();
                    if (old == null) continue;
                    for (int i = 0; i < old.length(); i++) {
                        long timestamp = old.optLong(i);
                        if (timestamp <= now && timestamp > now - 60 * 60_000L) fresh.put(timestamp);
                        if(key.equals(number) && timestamp <= now && timestamp > now-windowMinutes*60_000L)count++;
                    }

                    if (fresh.length() > 0 && (next.length() < 499 || key.equals(number))) next.put(key, fresh);
                }
                if (record) {
                    JSONArray values = next.optJSONArray(number);
                    if (values == null) values = new JSONArray();
                    if (values.length() < 100) values.put(now);
                    next.put(number, values); prefs.edit().putString("entries", next.toString()).apply();
                }
                return count;
            } catch (org.json.JSONException e) { return 0; }
        }
    }
}
