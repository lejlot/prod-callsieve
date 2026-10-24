package com.countryguard.app;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.CancellationSignal;
import android.provider.ContactsContract;

public final class ContactLookup {
    public record Status(boolean available, boolean contact) {}
    public static Status find(Context context, String raw, CancellationSignal cancellation) {
        if (context.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED)
            return new Status(false, false);
        if (raw == null || raw.isBlank()) return new Status(true, false);
        try (Cursor cursor = context.getContentResolver().query(
                Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(raw)),
                new String[]{ContactsContract.PhoneLookup._ID}, null, null, null, cancellation)) {
            return new Status(cursor != null, cursor != null && cursor.moveToFirst());
        } catch (RuntimeException e) { return new Status(false, false); }
    }
}
