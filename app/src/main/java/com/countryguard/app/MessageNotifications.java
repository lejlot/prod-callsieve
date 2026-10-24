package com.countryguard.app;

import android.app.*;
import android.content.*;
import android.os.Build;

public final class MessageNotifications {
    public static void show(Context context,int id,String title,String detail) {
        NotificationManager manager=context.getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel("messages","Incoming messages",NotificationManager.IMPORTANCE_DEFAULT));
        manager.createNotificationChannel(new NotificationChannel("sms_status","Message service status",NotificationManager.IMPORTANCE_DEFAULT));
        Intent open=new Intent(context,MessagesActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent tap=PendingIntent.getActivity(context,id,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification n=new Notification.Builder(context,id<0?"sms_status":"messages").setSmallIcon(com.countryguard.app.R.drawable.ic_shield)
            .setContentTitle(title).setContentText(detail).setContentIntent(tap).setAutoCancel(true)
            .setVisibility(Notification.VISIBILITY_PRIVATE).build();
        try {manager.notify(id,n);}catch(SecurityException ignored){}
    }
}
