package com.countryguard.app;

import android.app.Activity;
import android.content.*;
import android.provider.Telephony;

public final class SmsSentReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent) {
        long id=intent.getLongExtra("smsId",-1);if(id<0)return;
        try(MessageStore store=new MessageStore(context)) {
            int status=store.sent(id,intent.getIntExtra("part",0),getResultCode()==Activity.RESULT_OK);
            if(status==0)return;
            ContentValues value=new ContentValues();value.put("type",status>0?Telephony.Sms.MESSAGE_TYPE_SENT:Telephony.Sms.MESSAGE_TYPE_FAILED);
            value.put("error_code",status>0?0:getResultCode());
            if(SmsRepository.isDefault(context))context.getContentResolver().update(ContentUris.withAppendedId(Telephony.Sms.CONTENT_URI,id),value,null,null);
            if(status<0)MessageNotifications.show(context,-3,"SMS was not fully sent","Check Sent in CallSieve. Some parts may have been sent; retrying can duplicate them.");
        }
    }
}
