package com.countryguard.app;

import android.net.Uri;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.telecom.Call;
import android.telecom.CallScreeningService;
import java.util.concurrent.atomic.AtomicBoolean;

public final class CountryScreeningService extends CallScreeningService {
    private final Handler main = new Handler(Looper.getMainLooper());
    @Override public void onScreenCall(Call.Details details) {
        if (details.getCallDirection() != Call.Details.DIRECTION_INCOMING) return;
        Uri handle = details.getHandle();
        String raw = handle != null && "tel".equals(handle.getScheme()) ? handle.getSchemeSpecificPart() : null;
        AtomicBoolean answered = new AtomicBoolean();
        CancellationSignal cancellation = new CancellationSignal();
        Runnable timeout = () -> {
            if (answered.compareAndSet(false, true)) {
                respond(details, raw, fallback());
                cancellation.cancel();
            }
        };
        main.postDelayed(timeout, 3000);
        new Thread(() -> {
            ProtectionPolicy.Result decision;
            try { decision = CallPolicy.evaluate(this, raw, cancellation, true); }
            catch (RuntimeException e) { decision = fallback(); }
            ProtectionPolicy.Result result = decision;
            main.post(() -> {
                if (answered.compareAndSet(false, true)) {
                    main.removeCallbacks(timeout);
                    respond(details, raw, result);
                }
            });
        }, "call-policy").start();
    }
    private ProtectionPolicy.Result fallback() {
        return new ProtectionPolicy.Result(ProtectionPolicy.Action.ALLOW, "", "", "Screening unavailable; allowed");
    }
    private void respond(Call.Details details, String raw, ProtectionPolicy.Result result) {
        boolean blocked = result.action() == ProtectionPolicy.Action.BLOCK;
        respondToCall(details, new CallResponse.Builder().setDisallowCall(blocked).setRejectCall(blocked)
            .setSilenceCall(result.action() == ProtectionPolicy.Action.SILENCE)
            .setSkipCallLog(false).setSkipNotification(false).build());
        try { new HistoryStore(this).add(raw, result); } catch (RuntimeException ignored) { }
    }
}
