package com.countryguard.app;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;
import java.util.List;
import java.util.Set;

public final class RuleEngine {
    private final PhoneNumberUtil phone = PhoneNumberUtil.getInstance();

    public record Decision(boolean blocked, String normalized, String region, String reason) {}

    public Decision evaluate(String raw, String homeRegion, boolean enabled,
                             Set<String> blockedRegions, Set<String> exceptions) {
        return evaluate(raw, homeRegion, enabled, false, blockedRegions, exceptions);
    }

    public Decision evaluate(String raw, String homeRegion, boolean enabled, boolean allowOnly,
                             Set<String> selectedRegions, Set<String> exceptions) {
        if (!enabled) return new Decision(false, "", "", "Protection is paused");
        if (raw == null || raw.isBlank()) return unknown(allowOnly);

        if (!raw.matches("[+0-9().\\s-]+")) return unknown(allowOnly);
        try {
            PhoneNumber number = phone.parse(raw, homeRegion);
            if (!phone.isPossibleNumber(number)) return unknown(allowOnly);
            String normalized = phone.format(number, PhoneNumberUtil.PhoneNumberFormat.E164);
            String region = phone.getRegionCodeForNumber(number);
            if (exceptions.contains(normalized)) {
                return new Decision(false, normalized, region == null ? "" : region, "Allowed by your exception list");
            }
            List<String> candidates = phone.getRegionCodesForCountryCode(number.getCountryCode());
            if (region == null || "001".equals(region) || !phone.getSupportedRegions().contains(region)) {
                if (allowOnly) return new Decision(true, normalized, "", "Country cannot be confirmed as allowed; blocked");

                boolean allBlocked = !candidates.isEmpty() && selectedRegions.containsAll(candidates);
                return new Decision(allBlocked, normalized, "", allBlocked
                    ? "All countries sharing this calling code are blocked"
                    : "Country cannot be determined; allowed");
            }
            boolean blocked = allowOnly ? !selectedRegions.contains(region) : selectedRegions.contains(region);
            return new Decision(blocked, normalized, region,
                allowOnly ? (blocked ? "Country is not on your allow list" : "Country is on your allow list")
                    : (blocked ? "Country is on your block list" : "Country is not blocked"));
        } catch (NumberParseException | IllegalArgumentException e) {
            return unknown(allowOnly);
        }
    }

    public String normalizeException(String raw, String homeRegion) {
        if (raw == null || !raw.matches("[+0-9().\\s-]+")) return null;
        try {
            PhoneNumber n = phone.parse(raw, homeRegion);
            return phone.isValidNumber(n) ? phone.format(n, PhoneNumberUtil.PhoneNumberFormat.E164) : null;
        } catch (NumberParseException e) { return null; }
    }

    private Decision unknown(boolean allowOnly) {
        return new Decision(allowOnly, "", "", allowOnly
            ? "Country cannot be confirmed as allowed; blocked" : "Country cannot be determined; allowed");
    }
}
