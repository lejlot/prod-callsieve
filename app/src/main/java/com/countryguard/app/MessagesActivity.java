package com.countryguard.app;

import android.Manifest;
import android.app.*;
import android.app.role.RoleManager;
import android.content.*;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Telephony;
import android.text.*;
import android.widget.*;
import java.text.DateFormat;
import java.util.*;

public final class MessagesActivity extends GuardActivity {
    private String folder="Inbox";
    private int limit=50;
    private int generation=0;
    private boolean composing=false;
    private boolean inboxVisible=false;
    private boolean rulesVisible=false;
    private final android.os.Handler refreshHandler=new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable refresh=()->{if(inboxVisible&&!isFinishing())render();};
    private final android.database.ContentObserver observer=new android.database.ContentObserver(refreshHandler) {
        @Override public void onChange(boolean selfChange) {refreshHandler.removeCallbacks(refresh);refreshHandler.postDelayed(refresh,500);}
    };
    private SharedPreferences draft;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);draft=getSharedPreferences("draft",MODE_PRIVATE);
        if(state!=null){folder=state.getString("folder","Inbox");composing=state.getBoolean("composing");rulesVisible=state.getBoolean("rulesVisible");}
        else acceptIntent(getIntent());
    }
    private void acceptIntent(Intent intent) {
        if(Intent.ACTION_SENDTO.equals(intent.getAction())&&intent.getData()!=null) {
            Uri uri=intent.getData();String scheme=uri.getScheme();
            if(!Set.of("sms","smsto","mms","mmsto").contains(scheme==null?"":scheme))return;
            String address=uri.getSchemeSpecificPart().split("\\?",2)[0];
            String body=intent.getStringExtra("sms_body");if(body==null)body=intent.getStringExtra(Intent.EXTRA_TEXT);
            draft.edit().putString("address",address).putString("body",body==null?"":body).apply();composing=true;
        }
    }
    @Override protected void onNewIntent(Intent intent) {super.onNewIntent(intent);setIntent(intent);acceptIntent(intent);render();}
    @Override protected void onSaveInstanceState(Bundle state){state.putString("folder",folder);state.putBoolean("composing",composing);state.putBoolean("rulesVisible",rulesVisible);super.onSaveInstanceState(state);}
    @Override protected void onResume(){super.onResume();if(rulesVisible)rules();else render();if(permission(Manifest.permission.READ_SMS))getContentResolver().registerContentObserver(Telephony.Sms.CONTENT_URI,true,observer);}
    @Override protected void onPause(){getContentResolver().unregisterContentObserver(observer);refreshHandler.removeCallbacks(refresh);super.onPause();}
    private boolean permission(String name){return checkSelfPermission(name)==PackageManager.PERMISSION_GRANTED;}
    private void render() {
        rulesVisible=false;inboxVisible=!composing;
        backAction=this::finish;
        int request=++generation;screen("Messages");
        LinearLayout setup=card("");
        boolean selected=SmsRepository.isDefault(this);
        toggle(setup,"SMS filtering","smsEnabled",this::render);
        if(!selected)row(setup,"!","Set up SMS inbox","Requires the default SMS app role",null,this::requestSmsRole,null);
        else {
            if(!permission(Manifest.permission.READ_SMS)||!permission(Manifest.permission.SEND_SMS))
                row(setup,"!","Grant SMS access","Needed to read and send messages",null,()->requestPermissions(new String[]{Manifest.permission.READ_SMS,Manifest.permission.RECEIVE_SMS,Manifest.permission.SEND_SMS,Manifest.permission.RECEIVE_MMS,Manifest.permission.RECEIVE_WAP_PUSH},21),null);
            if(android.os.Build.VERSION.SDK_INT>=33&&!permission(Manifest.permission.POST_NOTIFICATIONS))
                row(setup,"!","Enable notifications","New messages are currently silent",null,()->requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},22),null);
            else if(!getSystemService(NotificationManager.class).areNotificationsEnabled())
                row(setup,"!","Enable notifications","New messages are currently silent",null,()->startActivity(new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE,getPackageName())),null);
        }
        row(setup,"≡","SMS decision tree","Allow lists, senders, phrases & links",null,this::rules,null);
        if(!selected||!permission(Manifest.permission.READ_SMS))return;
        if(composing){compose();return;}
        action(body,"Write SMS",()->{composing=true;render();});
        LinearLayout nav=new LinearLayout(this);body.addView(nav);
        for(String name:new String[]{"Inbox","Filtered","Sent"}) {
            Button b=new Button(this);b.setText(name);b.setTextSize(12);b.setAllCaps(false);b.setSelected(folder.equals(name));
            b.setTextColor(folder.equals(name)?android.graphics.Color.WHITE:GREEN);
            android.graphics.drawable.GradientDrawable shape=new android.graphics.drawable.GradientDrawable();
            shape.setColor(folder.equals(name)?GREEN:android.graphics.Color.rgb(232,241,235));shape.setCornerRadius(dp(12));b.setBackground(shape);
            LinearLayout.LayoutParams tab=new LinearLayout.LayoutParams(0,dp(52),1);tab.setMargins(dp(2),dp(8),dp(2),dp(8));
            nav.addView(b,tab);b.setOnClickListener(v->{folder=name;limit=50;render();});
        }
        LinearLayout list=card(folder);label(list,"Loading…",14,false);
        String selectedFolder=folder;int pageLimit=limit;
        new Thread(()->{
            List<Message> values=new ArrayList<>();boolean more=false;String error=null;int pending=0;List<Long> mms=new ArrayList<>();
            try(MessageStore store=new MessageStore(this)) {
                Map<Long,String> filters=store.filters();
                try(Cursor c=getContentResolver().query(Telephony.Sms.CONTENT_URI,new String[]{"_id","address","body","date","type","read"},
                    selectedFolder.equals("Sent")?"type IN (2,4,5,6)":"type=1",null,"date DESC, _id DESC")) {
                    if(c!=null)while(c.moveToNext()) {
                        String why=filters.getOrDefault(c.getLong(0),"");
                        if(!selectedFolder.equals("Sent") && selectedFolder.equals("Filtered")==why.isEmpty())continue;
                        if(values.size()>=pageLimit){more=true;break;}
                        values.add(new Message(c.getLong(0),c.getString(1),c.getString(2),c.getLong(3),c.getInt(4),c.getInt(5)!=0,why));
                    }
                }
                try(Cursor c=store.pending()){pending=c.getCount();}
                try(Cursor c=store.mms()){while(c.moveToNext())mms.add(c.getLong(1));}
            }catch(RuntimeException e){error="Cannot read messages. Check SMS access and your default messaging app.";}
            boolean hasMore=more;String problem=error;int pendingCount=pending;
            runOnUiThread(()->{
                if(isFinishing()||isDestroyed()||request!=generation)return;list.removeAllViews();label(list,selectedFolder,19,true);
                if(problem!=null){label(list,problem,14,false);return;}
                if(pendingCount>0){label(list,pendingCount+" saved SMS need recovery to the inbox.",14,true);action(list,"Recover saved SMS",()->{
                    new Thread(()->{try{SmsRepository.recover(this);runOnUiThread(this::render);}catch(RuntimeException e){runOnUiThread(()->toast("Recovery is still pending. Keep CallSieve as default and check SMS access."));}},"recover-sms").start();
                });}
                if(!mms.isEmpty()&&selectedFolder.equals("Inbox")) {
                    label(list,mms.size()+" MMS notices received. Latest: "+date(mms.get(0)),14,true);
                    label(list,"MMS content is not available here. Switch to an MMS-capable messaging app and ask the sender to resend. The raw carrier notices are retained locally, but switching apps does not transfer them.",13,false);
                    action(list,"Choose another SMS app",()->startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)));
                }
                if(values.isEmpty())label(list,"No messages in this folder.",15,false);
                for(Message message:values) {
                    String status=message.type==5?"FAILED · ":message.type==4||message.type==6?"PENDING · ":message.type==2?"SENT · ":message.read?"":"NEW · ";
                    String preview=message.body==null?"":message.body.replaceAll("\\s+"," ");
                    if(preview.length()>80)preview=preview.substring(0,80)+"…";
                    LinearLayout item=RuleUi.row(list,"",status+message.address,preview+"\n"+date(message.time),null,settings,()->open(message),null);
                    item.setTag("Open message");
                }
                if(hasMore)action(list,"Show 50 more",()->{limit+=50;render();});
                action(list,"Refresh",this::render);
            });
        },"load-inbox").start();
    }
    private record Message(long id,String address,String body,long time,int type,boolean read,String reason){}
    private String date(long time){return DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(new Date(time));}
    private void requestSmsRole() {
        new AlertDialog.Builder(this).setTitle("Use the SMS-only inbox?")
            .setMessage("CallSieve can receive, send and filter text SMS. Existing system SMS stay on your phone.\n\nIt does not support RCS, MMS pictures or group MMS. Incoming MMS notices are saved, but you must switch apps and ask for a resend to read their content. If you rely on those features, keep your current messaging app and use CallSieve for calls.\n\nThe rules run locally. Link filtering moves any matching web link from a non-contact to Filtered; it does not check the website’s reputation.")
            .setNegativeButton("Keep current app",null).setPositiveButton("Continue",(d,w)->{
                RoleManager manager=getSystemService(RoleManager.class);
                if(manager==null||!manager.isRoleAvailable(RoleManager.ROLE_SMS)){toast("SMS role is unavailable on this device.");return;}
                startActivityForResult(manager.createRequestRoleIntent(RoleManager.ROLE_SMS),20);
            }).show();
    }
    private void rules() {
        rulesVisible=false;startActivity(new Intent(this,RulesActivity.class).putExtra("sms",true));
    }
    private String senderKey(String raw) {String n=new RuleEngine().normalizeException(raw,settings.homeRegion());return n==null?ProtectionPolicy.senderKey(raw):n;}
    private void open(Message message) {
        inboxVisible=false;
        ContentValues read=new ContentValues();read.put("read",1);read.put("seen",1);
        try{getContentResolver().update(ContentUris.withAppendedId(Telephony.Sms.CONTENT_URI,message.id),read,null,null);}catch(RuntimeException ignored){}
        getSystemService(NotificationManager.class).cancel((int)(message.id&0x7fffffff));
        backAction=this::render;
        screen(message.address==null?"Message":message.address);
        label(body,date(message.time)+(message.reason.isEmpty()?"":" · "+message.reason),14,false);
        TextView text=label(body,message.body==null?"":message.body,17,false);text.setTextIsSelectable(true);
        action(body,"Reply",()->{draft.edit().putString("address",message.address).putString("body","").apply();composing=true;render();});
        if(!message.reason.isEmpty())action(body,"Move this message to Inbox",()->{try(MessageStore store=new MessageStore(this)){store.filter(message.id,"");}folder="Inbox";render();});
        String key=senderKey(message.address);
        action(body,"Edit SMS decision tree",this::rules);
        action(body,"Back to messages",this::render);
    }
    private void compose() {
        LinearLayout c=card("Write text SMS");
        EditText to=field(c,"Phone number",true),message=field(c,"Message",false);
        message.setSingleLine(false);message.setMinLines(4);message.setMaxLines(8);
        to.setText(draft.getString("address",""));message.setText(draft.getString("body",""));
        TextWatcher save=new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int count,int after){}public void onTextChanged(CharSequence s,int a,int before,int count){draft.edit().putString("address",to.getText().toString()).putString("body",message.getText().toString()).apply();}public void afterTextChanged(Editable e){}};
        to.addTextChangedListener(save);message.addTextChangedListener(save);
        label(c,"Uses your phone’s default SMS SIM. Carrier charges may apply. Longer texts may use multiple SMS. Sent means accepted for sending, not delivered to the recipient.",13,false);
        Button send=action(c,"Send SMS",()->{});
        send.setOnClickListener(v->{
            String address=to.getText().toString().replaceAll("[\\s().-]","");String normalized=new RuleEngine().normalizeException(address,settings.homeRegion());if(normalized!=null)address=normalized;
            String target=address,text=message.getText().toString();send.setEnabled(false);
            new Thread(()->{
                try{SmsSender.send(this,target,text);runOnUiThread(()->{draft.edit().clear().apply();composing=false;folder="Sent";render();toast("Sending SMS. Check Sent for its status.");});}
                catch(RuntimeException e){runOnUiThread(()->{send.setEnabled(true);toast(e.getMessage()==null?"Could not send SMS.":e.getMessage());});}
            },"send-sms").start();
        });
        action(c,"Back · keep draft",()->{composing=false;render();});
    }
}
