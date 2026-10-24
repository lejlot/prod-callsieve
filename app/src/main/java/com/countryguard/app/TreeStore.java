package com.countryguard.app;

import android.content.Context;
import org.json.*;
import java.util.*;
import static com.countryguard.app.DecisionTree.*;
import static com.countryguard.app.ProtectionPolicy.Action.*;

public final class TreeStore {
    private final android.content.SharedPreferences prefs;
    private final SettingsStore settings;
    public TreeStore(Context c){prefs=c.getSharedPreferences("decisionTrees",0);settings=new SettingsStore(c);}
    public Node load(boolean sms){String raw=prefs.getString(sms?"sms":"calls",null);if(raw!=null)return decode(raw);Node n=migrate(sms);save(sms,n);return n;}
    public void save(boolean sms,Node n){validate(n,sms);if(!prefs.edit().putString(sms?"sms":"calls",encode(n)).commit())throw new IllegalStateException("Could not save tree");}
    public static String encode(Node n){try{return json(n).toString();}catch(JSONException e){throw new IllegalArgumentException(e);}}
    private static JSONObject json(Node n)throws JSONException{
        JSONObject j=new JSONObject().put("id",n.id).put("kind",n.kind.name()).put("action",n.action.name()).put("values",new JSONArray(n.values))
            .put("start",n.start).put("end",n.end).put("limit",n.limit).put("window",n.window).put("outside",n.outside).put("reason",n.reason).put("stage",n.legacyStage);
        if(n.kind!=Kind.OUTCOME)j.put("yes",json(n.yes)).put("no",json(n.no));return j;
    }
    public static Node decode(String raw){try{return read(new JSONObject(raw),0);}catch(JSONException e){throw new IllegalArgumentException("Invalid saved tree",e);}}
    private static Node read(JSONObject j,int depth)throws JSONException{
        if(depth>24)throw new IllegalArgumentException("Tree too deep");Node n=new Node(Kind.valueOf(j.getString("kind")));n.id=j.getString("id");n.action=ProtectionPolicy.Action.valueOf(j.getString("action"));
        n.start=j.optInt("start",480);n.end=j.optInt("end",1080);n.limit=j.optInt("limit",2);n.window=j.optInt("window",10);n.outside=j.optBoolean("outside");n.reason=j.optString("reason");n.legacyStage=j.optInt("stage",-1);
        JSONArray v=j.optJSONArray("values");if(v!=null)for(int i=0;i<v.length();i++)n.values.add(v.getString(i));
        if(n.kind!=Kind.OUTCOME){n.yes=read(j.getJSONObject("yes"),depth+1);n.no=read(j.getJSONObject("no"),depth+1);}return n;
    }
    public static Node copy(Node n){Node c=decode(encode(n));newIds(c);return c;}
    private static void newIds(Node n){n.id=UUID.randomUUID().toString();if(n.kind!=Kind.OUTCOME){newIds(n.yes);newIds(n.no);}}
    private Node gate(Kind kind,Node yes,Node no,Set<String> values){Node n=decision(kind,yes,no);n.values.addAll(values);return n;}
    private Node leaf(ProtectionPolicy.Action action,String why,int stage){Node n=outcome(action);n.reason=why;n.legacyStage=stage;return n;}
    private Node migrate(boolean sms){
        SettingsStore s=settings;Node n=leaf(ALLOW,"No matching restriction",8);
        if(!sms&&s.flag("silence"))n=decision(Kind.NON_CONTACT,leaf(SILENCE,"Unknown caller silenced",7),n);
        if(!sms&&s.flag("repeat")){Node r=decision(Kind.REPEAT,leaf(BLOCK,"Repeated caller",6),n);r.limit=s.number("repeatLimit",2);r.window=s.number("repeatWindow",10);n=decision(Kind.NON_CONTACT,r,copy(n));}
        if(sms&&s.flag("smsLinks"))n=decision(Kind.CONTACT,copy(n),decision(Kind.LINK,leaf(BLOCK,"Message contains a link",-1),n));
        if(sms&&!s.list("smsPhrases").isEmpty())n=gate(Kind.PHRASE,leaf(BLOCK,"Message contains a filtered phrase",-1),n,s.list("smsPhrases"));
        if((!sms||s.flag("smsCountries"))&&(s.allowOnly()||!s.countries().isEmpty())){Node c=gate(Kind.COUNTRY,leaf(BLOCK,"Country restriction",5),n,s.countries());c.outside=s.allowOnly();n=c;}
        if(!sms&&s.flag("quiet")){Node t=decision(Kind.TIME,leaf(BLOCK,"Blocked by schedule",4),n);t.start=s.number("quietStart",1320);t.end=s.number("quietEnd",480);t.outside=s.flag("quietOutside");n=s.flag("quietExceptContacts")?decision(Kind.CONTACT,copy(n),t):t;}
        if(s.flag(sms?"smsContactsOnly":"contactsOnly"))n=decision(Kind.NON_CONTACT,leaf(BLOCK,"Not a saved contact",3),n);
        if(!s.list("blockedPrefixes").isEmpty())n=gate(Kind.PREFIX,leaf(BLOCK,"Blocked number prefix",2),n,s.list("blockedPrefixes"));
        if(!s.list("blockedNumbers").isEmpty())n=gate(Kind.NUMBERS,leaf(BLOCK,"Number is blocked",2),n,s.list("blockedNumbers"));
        if(s.flag(sms?"smsAllowContacts":"allowContacts"))n=decision(Kind.CONTACT,leaf(ALLOW,"Saved contact allowed",1),n);
        if(!s.temporaryNumbers().isEmpty())n=decision(Kind.TEMPORARY,leaf(ALLOW,"Temporary permission",1),n);
        Set<String> allowed=new HashSet<>(s.exceptions());if(sms)allowed.addAll(s.list("smsTrusted"));
        if(!allowed.isEmpty())n=gate(Kind.NUMBERS,leaf(ALLOW,"Number explicitly allowed",1),n,allowed);
        return n;
    }
}
