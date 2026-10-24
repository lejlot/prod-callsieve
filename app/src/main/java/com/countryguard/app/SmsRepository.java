package com.countryguard.app;

import android.content.*;
import android.net.Uri;
import android.os.CancellationSignal;
import android.provider.Telephony;
import java.util.Set;

public final class SmsRepository {
    public static boolean isDefault(Context context) {
        android.app.role.RoleManager role = context.getSystemService(android.app.role.RoleManager.class);
        return role != null && role.isRoleHeld(android.app.role.RoleManager.ROLE_SMS);
    }
    public static String reason(Context context,String raw,String body) {
        if(!new SettingsStore(context).flag("smsEnabled"))return "";
        DecisionTree.Result result=TreePolicy.evaluate(context,new TreeStore(context).load(true),true,raw,body,TreePolicy.minute(),new CancellationSignal());
        return result.action()==ProtectionPolicy.Action.ALLOW?"":result.reason();
    }
    public static String preview(Context context,String raw,String body) {
        if(!new SettingsStore(context).flag("smsEnabled"))return "INBOX\nSMS filtering is paused";
        DecisionTree.Result result=TreePolicy.evaluate(context,new TreeStore(context).load(true),true,raw,body,TreePolicy.minute(),new CancellationSignal());
        return (result.action()==ProtectionPolicy.Action.ALLOW?"INBOX":"FILTERED")+"\n"+result.reason();
    }
    public static long persist(Context context,long journal,String address,String body,long stamp,int sub) {
        try(MessageStore store=new MessageStore(context)) {
            long existing=store.linked(journal);if(existing>=0)return existing;
            String reason;
            try {reason=reason(context,address,body);}catch(RuntimeException e){reason="";}
            ContentValues values=new ContentValues(); values.put("address",address);values.put("body",body);
            values.put("date",System.currentTimeMillis());values.put("date_sent",stamp);values.put("sub_id",sub);
            values.put("read",reason.isEmpty()?0:1);values.put("seen",reason.isEmpty()?0:1);
            values.put("thread_id",Telephony.Threads.getOrCreateThreadId(context,address));
            Uri uri=context.getContentResolver().insert(Telephony.Sms.Inbox.CONTENT_URI,values);
            if(uri==null)throw new IllegalStateException("SMS provider unavailable");
            long id=ContentUris.parseId(uri);
            store.filter(id,reason);store.link(journal,id);
            if(reason.isEmpty())MessageNotifications.show(context,(int)(id&0x7fffffff),"New SMS","Open CallSieve to read your message.");
            return id;
        }
    }
    public static void recover(Context context) {
        try(MessageStore store=new MessageStore(context);android.database.Cursor c=store.pending()) {
            while(c.moveToNext())persist(context,c.getLong(0),c.getString(1),c.getString(2),c.getLong(3),c.getInt(4));
        }
    }
}
