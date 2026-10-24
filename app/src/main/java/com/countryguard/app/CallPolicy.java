package com.countryguard.app;
import android.content.Context;
import android.os.CancellationSignal;
import java.util.Set;
public final class CallPolicy {
    public static ProtectionPolicy.Result evaluate(Context c,String raw,CancellationSignal signal,boolean record){
        SettingsStore s=new SettingsStore(c);
        RuleEngine.Decision number=new RuleEngine().evaluate(raw,s.homeRegion(),true,false,Set.of(),Set.of());
        if(!s.enabled())return new ProtectionPolicy.Result(ProtectionPolicy.Action.ALLOW,number.normalized(),number.region(),"Call filtering is paused",0);
        DecisionTree.Result result=TreePolicy.evaluate(c,new TreeStore(c).load(false),false,raw,"",TreePolicy.minute(),signal);
        if(record)new AttemptStore(c).count(number.normalized(),60,true);
        return new ProtectionPolicy.Result(result.action(),number.normalized(),number.region(),result.reason(),result.stage());
    }
}
