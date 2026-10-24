package com.countryguard.app;

import android.content.Context;
import android.os.CancellationSignal;
import java.time.LocalTime;
import java.util.*;
import java.util.regex.Pattern;

public final class TreePolicy {
    public static DecisionTree.Result evaluate(Context c,DecisionTree.Node root,boolean sms,String raw,String body,int minute,CancellationSignal signal){
        SettingsStore s=new SettingsStore(c);RuleEngine engine=new RuleEngine();
        String normalized=engine.evaluate(raw,s.homeRegion(),true,false,Set.of(),Set.of()).normalized();
        ContactLookup.Status contact=ContactLookup.find(c,raw,signal);
        return DecisionTree.evaluate(root,sms,n->switch(n.kind){
            case CONTACT -> contact.available()&&contact.contact();
            case NON_CONTACT -> contact.available()&&!contact.contact();
            case TIME -> ProtectionPolicy.scheduleBlocks(minute,n.start,n.end,n.outside);
            case COUNTRY -> engine.evaluate(raw,s.homeRegion(),true,n.outside,n.values,Set.of()).blocked();
            case NUMBERS -> ProtectionPolicy.listed(raw,normalized,n.values);
            case PREFIX -> ProtectionPolicy.prefixMatches(normalized,n.values);
            case TEMPORARY -> s.temporaryUntil(normalized)>System.currentTimeMillis();
            case REPEAT -> new AttemptStore(c).count(normalized,n.window,false)>=n.limit;
            case LINK -> ProtectionPolicy.LINK.matcher(body==null?"":body).find();
            case PHRASE -> n.values.stream().anyMatch(v->!v.isBlank()&&(body==null?"":body).toLowerCase(Locale.ROOT).contains(v.toLowerCase(Locale.ROOT)));
            default -> false;
        });
    }
    public static int minute(){LocalTime t=LocalTime.now();return t.getHour()*60+t.getMinute();}
}
