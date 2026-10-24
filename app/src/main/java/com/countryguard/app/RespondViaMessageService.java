package com.countryguard.app;

import android.app.Service;
import android.content.*;
import android.os.IBinder;

public final class RespondViaMessageService extends Service {
    @Override public IBinder onBind(Intent intent){return null;}
    @Override public int onStartCommand(Intent intent,int flags,int startId) {
        if(intent!=null&&"android.intent.action.RESPOND_VIA_MESSAGE".equals(intent.getAction())&&intent.getData()!=null) {
            String address=intent.getData().getSchemeSpecificPart().split("\\?",2)[0];
            String body=intent.getStringExtra(Intent.EXTRA_TEXT);
            try{SmsSender.send(this,address,body);}catch(RuntimeException e){MessageNotifications.show(this,-4,"Quick reply was not sent",e.getMessage());}
        }
        stopSelf(startId);return START_NOT_STICKY;
    }
}
