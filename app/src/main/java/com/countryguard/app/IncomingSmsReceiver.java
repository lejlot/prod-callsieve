package com.countryguard.app;

import android.content.*;
import android.provider.Telephony;
import android.telephony.SmsMessage;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public final class IncomingSmsReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent) {
        if(!Telephony.Sms.Intents.SMS_DELIVER_ACTION.equals(intent.getAction())||!SmsRepository.isDefault(context))return;
        SmsMessage[] messages=Telephony.Sms.Intents.getMessagesFromIntent(intent);
        if(messages==null||messages.length==0)return;
        String address=messages[0].getDisplayOriginatingAddress();
        if(address==null||address.isBlank())address="Unknown sender";
        StringBuilder joined=new StringBuilder();for(SmsMessage m:messages)if(m.getMessageBody()!=null)joined.append(m.getMessageBody());
        String body=joined.toString(),sender=address;long stamp=messages[0].getTimestampMillis();
        int sub=intent.getIntExtra("subscription",-1);
        PendingResult pending=goAsync();
        new Thread(()->{
            boolean saved=false;
            try(MessageStore store=new MessageStore(context)) {
                String source=sender+"\n"+stamp+"\n"+sub+"\n"+body;
                String fingerprint=android.util.Base64.encodeToString(MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8)),android.util.Base64.NO_WRAP);
                long journal=store.journal(fingerprint,sender,body,stamp,sub);
                saved=true;
                SmsRepository.persist(context,journal,sender,body,stamp,sub);
            }catch(Exception e){MessageNotifications.show(context,-1,"SMS needs attention",saved
                ? "Open CallSieve to recover a saved incoming message."
                : "An incoming SMS could not be saved. Check available storage and SMS permissions.");}
            finally {pending.finish();}
        },"receive-sms").start();
    }
}
