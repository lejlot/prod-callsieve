package com.countryguard.app;

import android.content.*;
import android.provider.Telephony;

public final class IncomingMmsReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent) {
        if(!Telephony.Sms.Intents.WAP_PUSH_DELIVER_ACTION.equals(intent.getAction())||!SmsRepository.isDefault(context))return;
        byte[] data=intent.getByteArrayExtra("data");if(data==null)return;
        try(MessageStore store=new MessageStore(context)){store.mms(data,intent.getIntExtra("subscription",-1));}
        MessageNotifications.show(context,-2,"MMS received — action needed","This app supports SMS only. Open Messages for details.");
    }
}
