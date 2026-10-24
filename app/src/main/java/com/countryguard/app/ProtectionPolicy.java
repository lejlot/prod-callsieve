package com.countryguard.app;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class ProtectionPolicy {
    public enum Action { ALLOW, BLOCK, SILENCE }
    public record Result(Action action, String normalized, String region, String reason, int stage) {
        public Result(Action action,String normalized,String region,String reason){this(action,normalized,region,reason,-1);}
    }
    public record Rules(boolean enabled, boolean contactsOnly, boolean quiet, int quietStart,
                        int quietEnd, boolean repeat, int repeatLimit, boolean silence,
                        Set<String> blocked, Set<String> prefixes, boolean allowContacts,
                        boolean quietOutside, boolean quietExceptContacts) {
        public Rules(boolean enabled,boolean contactsOnly,boolean quiet,int quietStart,int quietEnd,
                     boolean repeat,int repeatLimit,boolean silence,Set<String> blocked,Set<String> prefixes){
            this(enabled,contactsOnly,quiet,quietStart,quietEnd,repeat,repeatLimit,silence,blocked,prefixes,false,false,false);
        }
    }
    public static boolean quietNow(int minute, int start, int end) {
        return start == end || (start < end ? minute >= start && minute < end : minute >= start || minute < end);
    }
    public static boolean scheduleBlocks(int minute,int start,int end,boolean outside){
        return outside != quietNow(minute,start,end);
    }
    public static String prefix(String value) {
        String clean = value == null ? "" : value.replaceAll("[\\s().-]", "");
        return clean.matches("\\+[1-9][0-9]{0,14}") ? clean : null;
    }
    public static String senderKey(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
    }
    public static boolean listed(String raw, String normalized, Set<String> values) {
        return (!normalized.isEmpty() && values.contains(normalized)) || values.contains(senderKey(raw));
    }
    public static boolean prefixMatches(String normalized, Set<String> prefixes) {
        return !normalized.isEmpty() && prefixes.stream().anyMatch(normalized::startsWith);
    }
    public Result call(RuleEngine.Decision country, Rules rules, boolean trusted, boolean contact,
                       boolean contactsAvailable, int minute, int previousAttempts) {
        if (!rules.enabled()) return result(country,Action.ALLOW,"Call protection is paused",0);
        if (trusted) return result(country,Action.ALLOW,"Always allow: all filters skipped",1);
        if (rules.allowContacts() && contactsAvailable && contact)
            return result(country,Action.ALLOW,"Always allow contacts: saved contact skips every call filter",1);
        if (listed(country.normalized(),country.normalized(),rules.blocked())) return result(country,Action.BLOCK,"Number is blocked",2);
        if (prefixMatches(country.normalized(),rules.prefixes())) return result(country,Action.BLOCK,"Number matches a blocked prefix",2);
        if (rules.contactsOnly() && contactsAvailable && !contact) return result(country,Action.BLOCK,"This caller is not in your Contacts",3);
        if (rules.quiet() && !(rules.quietExceptContacts() && contactsAvailable && contact)
                && scheduleBlocks(minute,rules.quietStart(),rules.quietEnd(),rules.quietOutside()))
            return result(country,Action.BLOCK,rules.quietOutside()?"Outside your selected hours":"Inside your selected hours",4);
        if (country.blocked()) return result(country,Action.BLOCK,country.reason(),5);
        if (rules.repeat() && contactsAvailable && !contact && previousAttempts>=rules.repeatLimit()) return result(country,Action.BLOCK,"Repeat-call limit reached",6);
        if (rules.silence() && contactsAvailable && !contact) return result(country,Action.SILENCE,"Non-contact: ringtone silenced",7);
        return result(country,Action.ALLOW,"No blocking or silencing rule matched",8);
    }
    private Result result(RuleEngine.Decision country,Action action,String reason,int stage) {
        return new Result(action,country.normalized(),country.region(),reason,stage);
    }
    static final Pattern LINK = Pattern.compile("(?i)(?:https?://|www\\.|\\b(?:[a-z0-9-]+\\.)+(?:[a-z]{2,63})(?:[/?:#]|\\b))");
    public String sms(RuleEngine.Decision country, String raw, String body, boolean enabled,
                      boolean trusted, boolean contact, boolean contactsAvailable, boolean contactsOnly,
                      boolean countryRules, boolean filterLinks, Set<String> blocked, Set<String> prefixes,
                      Set<String> phrases) {
        if (!enabled || trusted) return "";
        if (listed(raw, country.normalized(), blocked)) return "Blocked sender";
        if (prefixMatches(country.normalized(), prefixes)) return "Blocked number prefix";
        if (contactsOnly && contactsAvailable && !contact) return "Sender is not a saved contact";
        if (countryRules && country.blocked()) return country.reason();
        String value = body == null ? "" : body.toLowerCase(Locale.ROOT);
        for (String phrase : phrases) if (!phrase.isBlank() && value.contains(phrase.toLowerCase(Locale.ROOT)))
            return "Matches a blocked phrase";
        if (filterLinks && !contact && LINK.matcher(value).find()) return "Link from an unfamiliar sender";
        return "";
    }
}
