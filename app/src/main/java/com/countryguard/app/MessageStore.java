package com.countryguard.app;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import java.util.*;

public final class MessageStore extends SQLiteOpenHelper {
    public MessageStore(Context context) { super(context,"messages.db",null,1); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE filters (sms_id INTEGER PRIMARY KEY, reason TEXT NOT NULL)");
        db.execSQL("CREATE TABLE journal (_id INTEGER PRIMARY KEY, fingerprint TEXT UNIQUE NOT NULL, address TEXT, body TEXT, stamp INTEGER, sub INTEGER, sms_id INTEGER DEFAULT -1)");
        db.execSQL("CREATE TABLE mms (_id INTEGER PRIMARY KEY, stamp INTEGER, sub INTEGER, pdu BLOB NOT NULL)");
        db.execSQL("CREATE TABLE sends (sms_id INTEGER, part INTEGER, result INTEGER, PRIMARY KEY(sms_id,part))");
    }
    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion) { }
    public long journal(String fingerprint,String address,String body,long stamp,int sub) {
        ContentValues v=new ContentValues();v.put("fingerprint",fingerprint);v.put("address",address);v.put("body",body);v.put("stamp",stamp);v.put("sub",sub);
        getWritableDatabase().insertWithOnConflict("journal",null,v,SQLiteDatabase.CONFLICT_IGNORE);
        try(Cursor c=getReadableDatabase().query("journal",new String[]{"_id"},"fingerprint=?",new String[]{fingerprint},null,null,null)) {c.moveToFirst();return c.getLong(0);}
    }
    public long linked(long journalId) {
        try(Cursor c=getReadableDatabase().rawQuery("SELECT sms_id FROM journal WHERE _id=?",new String[]{""+journalId})) {return c.moveToFirst()?c.getLong(0):-1;}
    }
    public void link(long journalId,long smsId) {ContentValues v=new ContentValues();v.put("sms_id",smsId);getWritableDatabase().update("journal",v,"_id=?",new String[]{""+journalId});}
    public void filter(long id,String reason) {
        if(reason.isEmpty()) {getWritableDatabase().delete("filters","sms_id=?",new String[]{""+id});return;}
        ContentValues v=new ContentValues();v.put("sms_id",id);v.put("reason",reason);getWritableDatabase().insertWithOnConflict("filters",null,v,SQLiteDatabase.CONFLICT_REPLACE);
    }
    public Map<Long,String> filters() {
        Map<Long,String> values=new HashMap<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT sms_id,reason FROM filters",null)) {while(c.moveToNext())values.put(c.getLong(0),c.getString(1));}return values;
    }
    public Cursor pending() {return getReadableDatabase().rawQuery("SELECT _id,address,body,stamp,sub FROM journal WHERE sms_id=-1 ORDER BY stamp DESC",null);}
    public void mms(byte[] pdu,int sub) {ContentValues v=new ContentValues();v.put("stamp",System.currentTimeMillis());v.put("sub",sub);v.put("pdu",pdu);getWritableDatabase().insertOrThrow("mms",null,v);}
    public Cursor mms() {return getReadableDatabase().rawQuery("SELECT _id,stamp,sub FROM mms ORDER BY stamp DESC",null);}
    public void sending(long id,int count) {
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{
            for(int part=0;part<count;part++) {ContentValues v=new ContentValues();v.put("sms_id",id);v.put("part",part);v.put("result",0);db.insertOrThrow("sends",null,v);}db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }

    public int sent(long id,int part,boolean success) {
        ContentValues v=new ContentValues();v.put("result",success?1:-1);getWritableDatabase().update("sends",v,"sms_id=? AND part=? AND result=0",new String[]{""+id,""+part});
        boolean pending=false,failed=false;
        try(Cursor c=getReadableDatabase().rawQuery("SELECT result FROM sends WHERE sms_id=?",new String[]{""+id})) {
            if(c.getCount()==0)return 0;
            while(c.moveToNext()){pending|=c.getInt(0)==0;failed|=c.getInt(0)==-1;}
        }return failed?-1:pending?0:1;
    }
}
