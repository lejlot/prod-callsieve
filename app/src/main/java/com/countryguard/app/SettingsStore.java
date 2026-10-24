package com.countryguard.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.telephony.TelephonyManager;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class SettingsStore {
    private final SharedPreferences prefs;
    private final Context context;
    public SettingsStore(Context context) {
        this.context = context.getApplicationContext();
        prefs = context.getSharedPreferences("rules", Context.MODE_PRIVATE);
    }
    public boolean enabled() { return prefs.getBoolean("enabled", true); }
    public void setEnabled(boolean value) { prefs.edit().putBoolean("enabled", value).apply(); }
    public boolean allowOnly() { return prefs.getBoolean("allowOnly", false); }
    public void setAllowOnly(boolean value) { prefs.edit().putBoolean("allowOnly", value).apply(); }
    private String countryKey() { return allowOnly() ? "allowedCountries" : "countries"; }
    public Set<String> countries() { return new HashSet<>(prefs.getStringSet(countryKey(), Set.of())); }
    public void setCountry(String iso, boolean selected) {
        Set<String> values = countries();
        if (selected) values.add(iso); else values.remove(iso);
        prefs.edit().putStringSet(countryKey(), values).apply();
    }
    public Set<String> exceptions() { return new HashSet<>(prefs.getStringSet("exceptions", Set.of())); }
    public void addException(String number) {
        Set<String> values = exceptions(); values.add(number);
        prefs.edit().putStringSet("exceptions", values).apply();
    }
    public void removeException(String number) {
        Set<String> values = exceptions(); values.remove(number);
        prefs.edit().putStringSet("exceptions", values).apply();
    }
    public String homeRegion() {
        String saved = prefs.getString("home", null);
        if (saved != null) return saved;
        String region = "";
        try {
            TelephonyManager tm = context.getSystemService(TelephonyManager.class);
            if (tm != null) region = tm.getSimCountryIso().toUpperCase(Locale.ROOT);
        } catch (RuntimeException ignored) { }
        if (!PhoneNumberUtil.getInstance().getSupportedRegions().contains(region))
            region = Locale.getDefault().getCountry();
        if (!PhoneNumberUtil.getInstance().getSupportedRegions().contains(region)) region = "US";
        return region;
    }
    public void setHomeRegion(String iso) { prefs.edit().putString("home", iso).apply(); }
    public boolean flag(String key) { return prefs.getBoolean(key, false); }
    public void flag(String key, boolean value) { prefs.edit().putBoolean(key, value).apply(); }
    public int number(String key, int fallback) { return prefs.getInt(key, fallback); }
    public void setNumber(String key, int value) { prefs.edit().putInt(key, value).apply(); }
    public Set<String> list(String key) { return new HashSet<>(prefs.getStringSet(key, Set.of())); }
    public void list(String key, Set<String> values) { prefs.edit().putStringSet(key, new HashSet<>(values)).apply(); }
    public void add(String key, String value) { Set<String> values = list(key); values.add(value); list(key, values); }
    public void remove(String key, String value) { Set<String> values = list(key); values.remove(value); list(key, values); }
    public void temporary(String number, long until) { prefs.edit().putLong("temporary:" + number, until).apply(); }
    public long temporaryUntil(String number) { return prefs.getLong("temporary:" + number, 0); }
    public java.util.Map<String, Long> temporaryNumbers() {
        java.util.Map<String, Long> values = new java.util.TreeMap<>();
        prefs.getAll().forEach((key, value) -> {
            if (key.startsWith("temporary:") && value instanceof Long && (Long) value > System.currentTimeMillis())
                values.put(key.substring(10), (Long) value);
        });
        return values;
    }
}
