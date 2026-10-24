package com.countryguard.app;

import android.Manifest;
import android.app.PendingIntent;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.Telephony;
import android.telephony.*;
import java.util.ArrayList;

public final class SmsSender {
    public static void send(Context context,String address,String body) {
        if(!SmsRepository.isDefault(context))throw new IllegalStateException("Choose CallSieve as the default SMS app first.");
        if(context.checkSelfPermission(Manifest.permission.SEND_SMS)!=PackageManager.PERMISSION_GRANTED)
            throw new IllegalStateException("Grant SMS access first.");
        if(address==null||!address.matches("\\+?[0-9]{3,15}")||body==null||body.isBlank())throw new IllegalArgumentException("Enter a phone number and message.");
        int sub=SubscriptionManager.getDefaultSmsSubscriptionId();
        if(!SubscriptionManager.isValidSubscriptionId(sub))throw new IllegalStateException("Choose a default SMS SIM in your phone settings, then try again.");
        SmsManager sms=SmsManager.getSmsManagerForSubscriptionId(sub);
        ArrayList<String> parts=sms.divideMessage(body);
        ContentValues values=new ContentValues();values.put("address",address);values.put("body",body);values.put("date",System.currentTimeMillis());
        values.put("read",1);values.put("seen",1);values.put("type",Telephony.Sms.MESSAGE_TYPE_OUTBOX);values.put("sub_id",sub);
        values.put("thread_id",Telephony.Threads.getOrCreateThreadId(context,address));
        Uri uri=context.getContentResolver().insert(Telephony.Sms.CONTENT_URI,values);
        if(uri==null)throw new IllegalStateException("Could not save the outgoing message.");
        long id=ContentUris.parseId(uri);
        try(MessageStore store=new MessageStore(context)) {
            store.sending(id,parts.size());ArrayList<PendingIntent> callbacks=new ArrayList<>();
            for(int i=0;i<parts.size();i++) {
                Intent report=new Intent(context,SmsSentReceiver.class).setAction("com.countryguard.app.SMS_SENT")
                    .setData(Uri.parse("countryguard://sent/"+id+"/"+i)).putExtra("smsId",id).putExtra("part",i);
                callbacks.add(PendingIntent.getBroadcast(context,0,report,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE));
            }
            sms.sendMultipartTextMessage(address,null,parts,callbacks,null);
        }catch(RuntimeException e){
            ContentValues failed=new ContentValues();failed.put("type",Telephony.Sms.MESSAGE_TYPE_FAILED);context.getContentResolver().update(uri,failed,null,null);throw e;
        }
    }
}
