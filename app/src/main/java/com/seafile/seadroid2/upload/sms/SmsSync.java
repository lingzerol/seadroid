package com.seafile.seadroid2.upload.sms;

import android.Manifest;
import android.content.SyncResult;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Build;
import android.provider.CallLog;
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
import com.seafile.seadroid2.upload.callLog.CallLogSync;
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
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

public class SmsSync extends UploadSync {
    private static final String DEBUG_TAG = "SmsUploadSync";
    private static final String CACHE_NAME = "SmsSync";
    private final String BASE_DIR = super.BASE_DIR + "/Sms";

    private SmsCursor previous = null;
    private LinkedList<Integer> leftSmsTypes = Lists.newLinkedList();
    private int synNum = 0;

    public SmsSync(UploadSyncAdapter adapter) {
        super(adapter, UploadManager.SMS_SYNC);
    }

    public String getDirectoryPath(){
        return Utils.pathJoin(getDataPath(), BASE_DIR);
    }

    @Override
    public String getMediaName() {
        return "Sms";
    }

    public void uploadContents(SyncResult syncResult, DataManager dataManager) throws SeafException, InterruptedException {
        Utils.utilsLogInfo(true, "========Starting to upload Sms...");
        if (previous == null) {
            if (synNum > CLEAR_CACHE_BOUNDARY) {
                Utils.utilsLogInfo(true, "========Clear Sms cache...");
                dbHelper.cleanCache();
                synNum = 0;
            } else {
                ++synNum;
            }
        }
        if (ContextCompat.checkSelfPermission(adapter.getContext(), Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            Utils.utilsLogInfo(true, "Read Sms permission is not granted.");
            return;
        }

        if (previous != null) {
            Utils.utilsLogInfo(true, "========Continue uploading previous cursor========");
            previous = (SmsCursor) iterateCursor(syncResult, dataManager, previous);
            if (isCancelled()) {
                Utils.utilsLogInfo(true, "========Cancel uploading========");
                return;
            }
        }

        List<Integer> uploadSmsTypes = leftSmsTypes;
        if (uploadSmsTypes == null || uploadSmsTypes.isEmpty()) {
            uploadSmsTypes = Lists.newArrayList(
                    Telephony.Sms.MESSAGE_TYPE_INBOX,
                    Telephony.Sms.MESSAGE_TYPE_OUTBOX,
                    Telephony.Sms.MESSAGE_TYPE_DRAFT,
                    Telephony.Sms.MESSAGE_TYPE_FAILED,
                    Telephony.Sms.MESSAGE_TYPE_SENT,
                    Telephony.Sms.MESSAGE_TYPE_QUEUED
            );
        }
        leftSmsTypes = Lists.newLinkedList();
        leftSmsTypes.addAll(uploadSmsTypes);

        File repoFile = dataManager.getLocalRepoFile(getRepoName(), getRepoID(), getDirectoryPath());
        String repoPath = repoFile.getAbsolutePath();
        for (Integer smsType : uploadSmsTypes) {
            leftSmsTypes.removeFirst();
            adapter.createDirectory(dataManager, getRepoID(), Utils.pathJoin(getDirectoryPath(), getSmsTypeName(smsType)));
            Cursor cursor = adapter.getContentResolver().query(Telephony.Sms.CONTENT_URI,
                    null,
                    Telephony.Sms.TYPE + " = ? ",
                    new String[]{String.valueOf(smsType)},
                    Telephony.Sms.DATE + " ASC");
            SmsCursor smsCursor = new SmsCursor(repoPath, getSmsTypeName(smsType), cursor);
            if(cursor.getCount() > 0) {
                previous = (SmsCursor) iterateCursor(syncResult, dataManager, smsCursor);
            }else{
                Utils.utilsLogInfo(true, "=======Sms Sync: the sms type " + getSmsTypeName(smsType) + " is cancelled because the directory is empty");
                previous = null;
            }
            if(isCancelled()){
                Utils.utilsLogInfo(true, "========Cancel uploading========");
                break;
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

    private String getSmsTypeName(int type){
        switch(type){
            case Telephony.Sms.MESSAGE_TYPE_INBOX:
                return "Inbox";
            case Telephony.Sms.MESSAGE_TYPE_OUTBOX:
                return "Outbox";
            case Telephony.Sms.MESSAGE_TYPE_ALL:
                return "All";
            case Telephony.Sms.MESSAGE_TYPE_DRAFT:
                return "Draft";
            case Telephony.Sms.MESSAGE_TYPE_FAILED:
                return "Failed";
            case Telephony.Sms.MESSAGE_TYPE_SENT:
                return "Sent";
            case Telephony.Sms.MESSAGE_TYPE_QUEUED:
                return "Queued";
            default:
                return "Unknown";
        }
    }

    private File generateSms(String path, JSONObject jsonObject, long timestamp, int type){
        if(jsonObject == null){
            return null;
        }
        try {
            jsonObject.put("Date", getDateStr(timestamp));
            jsonObject.put("SmsTypeName", getSmsTypeName(type));
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

    @Override
    protected Comparator<SeafDirent> getComparator() {
        return new Comparator<SeafDirent>() {
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
        };
    }


    private class SmsCursor extends UploadSync.SyncCursor{
        private String repoPath = null;
        private String bucketName = null;
        private Cursor cursor = null;
        private List<File> localFiles = Lists.newArrayList();

        public SmsCursor(String repoPath, String bucketName, Cursor cursor){
            this.repoPath = repoPath;
            this.bucketName = bucketName;
            this.cursor = cursor;
            File localDir = new File(Utils.pathJoin(repoPath, bucketName));
            if(!localDir.exists()){
                localDir.mkdirs();
            }
        }

        public String getBucketName(){
            return bucketName;
        }

        public int getCount(){
            if(cursor == null){
                return 0;
            }
            return cursor.getCount();
        }

        public int getPosition(){
            if(cursor == null){
                return 0;
            }
            return cursor.getPosition();
        }

        public boolean isBeforeFirst(){
            return cursor.isBeforeFirst();
        }

        public boolean moveToNext(){
            if(cursor == null){
                return false;
            }
            return cursor.moveToNext();
        }

        public boolean isAfterLast(){
            if(cursor == null){
                return false;
            }
            return cursor.isAfterLast();
        }

        public String getFilePath(){
            if(cursor == null || repoPath == null || bucketName == null){
                return null;
            }
            String fileName = getFileName();
            if(fileName == null){
                return null;
            }
            return Utils.pathJoin(repoPath, bucketName, fileName);
        }

        public String getFileName(){
            if(cursor == null){
                return null;
            }
            String address = cursor.getString(cursor.getColumnIndex(Telephony.Sms.ADDRESS));
            long date = cursor.getLong(cursor.getColumnIndex(Telephony.Sms.DATE));
            return getDateStr(date) + "(" + address + ").json";
        }

        public File getFile(){
            if(cursor == null){
                return null;
            }
            String filePath = getFilePath();
            if(filePath == null){
                return null;
            }
            long timestamp = cursor.getLong(cursor.getColumnIndex(Telephony.Sms.DATE));
            int type = cursor.getInt(cursor.getColumnIndex(Telephony.Sms.TYPE));
            JSONObject jsonObject = UploadSync.SyncCursor.ToJson(cursor);
            if(jsonObject == null){
                return null;
            }
            File file = generateSms(filePath, jsonObject, timestamp, type);
            if(file == null){
                return null;
            }
            localFiles.add(file);
            return file;
        }

        public long getFileSize(){
            File file = getFile();
            if(file == null){
                return 0;
            }
            return file.length();
        }

        public long getFileModified(){
            if(cursor == null){
                return 0;
            }
            return cursor.getLong(cursor.getColumnIndex(CallLog.Calls.DATE));
        }

        public void postProcess(){
            for(File file:localFiles){
                if(file != null && file.exists()){
                    file.delete();
                }
            }
            localFiles.clear();
        }

        @Override
        public int compareToCacheOrder(SeafDirent item) {
            if(item == null){
                return 1;
            }
            if(cursor == null){
                return -1;
            }
            long date = getFileModified();
            String dateStr = item.name;
            dateStr = dateStr.substring(0, dateStr.lastIndexOf("."));
            long timestamp = getTimeStamp(dateStr);
            if(date < timestamp){
                return -1;
            }else if(date == timestamp){
                return 0;
            }
            return 1;
        }

        @Override
        public int compareToCacheMore(SeafDirent item) {
            return (compareToCacheOrder(item) < 0)?1:0;
        }
    }
}
