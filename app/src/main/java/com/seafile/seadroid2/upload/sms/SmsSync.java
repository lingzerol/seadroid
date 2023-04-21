package com.seafile.seadroid2.upload.sms;

import android.Manifest;
import android.content.SyncResult;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.provider.Telephony;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.widget.Switch;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.google.common.collect.Lists;
import com.seafile.seadroid2.SeafException;
import com.seafile.seadroid2.data.DataManager;
import com.seafile.seadroid2.data.DirentCache;
import com.seafile.seadroid2.data.SeafDirent;
import com.seafile.seadroid2.upload.UploadManager;
import com.seafile.seadroid2.upload.UploadSync;
import com.seafile.seadroid2.upload.UploadSyncAdapter;
import com.seafile.seadroid2.util.Utils;

import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SmsSync extends UploadSync {
    private static final String DEBUG_TAG = "SmsUploadSync";
    private static final String CACHE_NAME = "SmsSync";
    private final String BASE_DIR = super.BASE_DIR + "/Sms";

    private Cursor previous = null;
    private int synNum = 0;

    public SmsSync(UploadSyncAdapter adapter) {
        super(adapter, UploadManager.SMS_SYNC);
    }

    public String getDirectoryPath(){
        return Utils.pathJoin(getDataPath(), BASE_DIR);
    }

    public void uploadContents(SyncResult syncResult, DataManager dataManager) throws SeafException, InterruptedException{
        Utils.utilsLogInfo(true, "========Starting to upload Sms...");
        synNum = 100;
        if(previous == null){
            if(synNum > 10) {
                Utils.utilsLogInfo(true, "========Clear Sms cache...");
                dbHelper.cleanCache();
                synNum = 0;
            }else {
                ++synNum;
            }
        }
        if (ContextCompat.checkSelfPermission(adapter.getContext(), Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED){
            Utils.utilsLogInfo(true, "Read Sms permission is not granted.");
            return;
        }
        Cursor cursor = previous;
        if(cursor == null) {
            cursor = adapter.getContentResolver().query(Telephony.Sms.CONTENT_URI,
                    null,
                    null,
                    null,
                    Telephony.Sms.DATE + " ASC");
        }
        int fileIter = cursor.getPosition();
        int fileNum = cursor.getCount();
        Utils.utilsLogInfo(true, "========Found ["+ fileIter+"/"+fileNum+"] Sms ========");
        List<File> SmsList = Lists.newArrayList();
        try {
            adapter.createDirectory(dataManager, getRepoID(), getDirectoryPath());
            File repoFile = dataManager.getLocalRepoFile(getRepoName(), getRepoID(), getDirectoryPath());
            if(!repoFile.exists() && !repoFile.mkdirs()){
                Utils.utilsLogInfo(true, "Failed to create " + getAccountSignature() + "-" + getRepoName() + ":" + Utils.pathJoin(getDataPath(), BASE_DIR) + " dir");
                Log.d(DEBUG_TAG, "Failed to create " + getAccountSignature() + "-" + getRepoName() + ":" + Utils.pathJoin(getDataPath(), BASE_DIR) + " dir");
                return;
            }
            String cacheName = CACHE_NAME + "-" + repoID;
            DirentCache cache = getCache(cacheName, getRepoID(),getDirectoryPath(), dataManager, new Comparator<SeafDirent>() {
                @Override
                public int compare(SeafDirent o1, SeafDirent o2) {
                    String date1 = o1.name.substring(0, o1.name.lastIndexOf("."));
                    String date2 = o2.name.substring(0, o2.name.lastIndexOf("."));
                    long timestamp1 = getTimeStamp(date1);
                    long timestamp2 = getTimeStamp(date2);
                    if (timestamp1 < timestamp2) {
                        return -1;
                    } else if (timestamp1 == timestamp2) {
                        return 0;
                    }
                    return 1;
                }
            });
            if(cursor.isBeforeFirst() && !cursor.isAfterLast()){
                cursor.moveToNext();
            }
            if (cache != null) {
                int n = cache.getCount();
                boolean done = false;
                for (int i = 0; i < cache.getCount(); ++i) {
                    if (cursor.isAfterLast()) {
                        break;
                    }
                    String dateStr = cache.get(i).name;
                    dateStr = dateStr.substring(0, dateStr.lastIndexOf("."));
                    long timestamp = getTimeStamp(dateStr);
                    while (!isCancelled() && cursor.getLong(cursor.getColumnIndex(Telephony.Sms.DATE)) <= timestamp) {
                        String address = cursor.getString(cursor.getColumnIndex(Telephony.Sms.ADDRESS));
                        String body = cursor.getString(cursor.getColumnIndex(Telephony.Sms.BODY));
                        String subject = cursor.getString(cursor.getColumnIndex(Telephony.Sms.SUBJECT));
                        long date = cursor.getLong(cursor.getColumnIndex(Telephony.Sms.DATE));
                        int type = cursor.getInt(cursor.getColumnIndex(Telephony.Sms.TYPE));
                        String filePath = getFilePath(repoFile.getAbsolutePath(), date, address);
                        if (date < timestamp && !dbHelper.isUploaded(getAccountSignature(), filePath)) {
                            File file = generateSms(filePath, address, subject, body, date, type);
                            if (file != null && file.exists()) {
                                SmsList.add(file);
                                adapter.uploadFile(dataManager, file, getRepoID(), getRepoName(), getDirectoryPath());
                            }else{
                                syncResult.stats.numSkippedEntries++;
                            }
                        }
                        ++fileIter;
                        if(!cursor.moveToNext()){
                            break;
                        }
                    }
                    if (i == n - 1) {
                        done = true;
                    }
                    if (isCancelled()) {
                        break;
                    }
                }
                if (done) {
                    cache.delete();
                }
            }
            while (!isCancelled() && !cursor.isAfterLast()) {
                String address = cursor.getString(cursor.getColumnIndex(Telephony.Sms.ADDRESS));
                String body = cursor.getString(cursor.getColumnIndex(Telephony.Sms.BODY));
                String subject = cursor.getString(cursor.getColumnIndex(Telephony.Sms.SUBJECT));
                long date = cursor.getLong(cursor.getColumnIndex(Telephony.Sms.DATE));
                int type = cursor.getInt(cursor.getColumnIndex(Telephony.Sms.TYPE));
                String filePath = getFilePath(repoFile.getAbsolutePath(), date, address);
                if (!dbHelper.isUploaded(getAccountSignature(), filePath)) {
                    File file = generateSms(filePath, address, subject, body, date, type);
                    if (file != null && file.exists()) {
                        SmsList.add(file);
                        adapter.uploadFile(dataManager, file, getRepoID(), getRepoName(), getDirectoryPath());
                    }else{
                        syncResult.stats.numSkippedEntries++;
                    }
                }
                ++fileIter;
                if (!cursor.moveToNext()) {
                    break;
                }
            }
        }catch (IOException e){
            Log.e(DEBUG_TAG, "Failed to get cache file.", e);
            Utils.utilsLogInfo(true, "Failed to get cache file: "+ e.toString());
        }catch (Exception e){
            Utils.utilsLogInfo(true, e.toString());
        }
        Utils.utilsLogInfo(true,"=======Have checked ["+Integer.toString(fileIter)+"/"+Integer.toString(fileNum)+"] Sms======");
        Utils.utilsLogInfo(true,"=======waitForUploads===");
        adapter.waitForUploads();
        adapter.checkUploadResult(this, syncResult);
        if(cursor.isAfterLast()){
            cursor.close();
            previous = null;
        }else{
            previous = cursor;
        }
        for(File file:SmsList){
            if(file != null && file.exists()){
                file.delete();
            }
        }
        Utils.utilsLogInfo(true, "========End uploading sms...");
    }

    private String getFilePath(String repoPath, long date, String address){
        return Utils.pathJoin(repoPath, getDateStr(date) + "(" + address + ").json");
    }

    private long getTimeStamp(String dateStr){
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH'h'-mm'm'-ss's'-SSS", Locale.getDefault());
        long timestamp = 0;
        try {
            Date date = dateFormat.parse(dateStr);
            if(date != null) {
                timestamp = date.getTime();
            }
        } catch (ParseException e) {
            Log.d(DEBUG_TAG, e.toString());
        }
        return timestamp;
    }

    private String getDateStr(long timestamp){
        Date date = new Date(timestamp);
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH'h'-mm'm'-ss's'-SSS", Locale.getDefault());
        return dateFormat.format(date);
    }

    private File generateSms(String path, String address, String subject, String body, long date, int type){
        try {
//            if(subject != null){
//                subject = new String(subject.getBytes(), StandardCharsets.UTF_8);
//            }
//            if(body != null){
//                body = new String(body.getBytes(), StandardCharsets.UTF_8);
//            }
//            Utils.utilsLogInfo(true, "Sms content: " + address + " " + subject + " " + body + " " + Long.toString(date) + " " + Integer.toString(type));
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("Address", address);
            jsonObject.put("Subject", subject);
            jsonObject.put("Body", body);
            jsonObject.put("TimeStamp", date);
            jsonObject.put("Date", getDateStr(date));
            switch(type){
                case Telephony.Sms.MESSAGE_TYPE_INBOX:
                    jsonObject.put("SmsType", "Inbox");
                    break;
                case Telephony.Sms.MESSAGE_TYPE_OUTBOX:
                    jsonObject.put("SmsType", "Outbox");
                    break;
                case Telephony.Sms.MESSAGE_TYPE_ALL:
                    jsonObject.put("SmsType", "All");
                    break;
                case Telephony.Sms.MESSAGE_TYPE_DRAFT:
                    jsonObject.put("SmsType", "Draft");
                    break;
                case Telephony.Sms.MESSAGE_TYPE_FAILED:
                    jsonObject.put("SmsType", "Failed");
                    break;
                case Telephony.Sms.MESSAGE_TYPE_SENT:
                    jsonObject.put("SmsType", "Sent");
                    break;
                case Telephony.Sms.MESSAGE_TYPE_QUEUED:
                    jsonObject.put("SmsType", "Queued");
                    break;
                default:
                    jsonObject.put("SmsType", "Unknown");
                    break;
            }
            FileWriter fileWriter = new FileWriter(path, false);
            fileWriter.write(jsonObject.toString(4));
            fileWriter.close();
            return new File(path);
        }catch (Exception e){
            Utils.utilsLogInfo(true, "Failed to create sms file " + path + ": " + e.toString());
            Log.d(DEBUG_TAG, "Failed to create sms file " + path + ": " + e.toString());
        }
        return null;
    }
}
