package com.seafile.seadroid2.upload.callLog;

import android.Manifest;
import android.content.SyncResult;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.provider.CallLog;
import android.support.v4.content.ContextCompat;
import android.util.Log;

import com.google.common.collect.Lists;
import com.seafile.seadroid2.SeafException;
import com.seafile.seadroid2.data.DataManager;
import com.seafile.seadroid2.data.DirentCache;
import com.seafile.seadroid2.data.SeafDirent;
import com.seafile.seadroid2.upload.UploadManager;
import com.seafile.seadroid2.upload.UploadSync;
import com.seafile.seadroid2.upload.UploadSyncAdapter;
import com.seafile.seadroid2.util.Utils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class CallLogSync extends UploadSync {
    private static final String DEBUG_TAG = "CallLogUploadSync";
    private static final String CACHE_NAME = "CallLogSync";
    private final String BASE_DIR = super.BASE_DIR + "/CallLog";

    private Cursor previous = null;
    private int synNum = 0;

    public CallLogSync(UploadSyncAdapter adapter) {
        super(adapter, UploadManager.CALLLOG_SYNC);
    }

    public String getDirectoryPath(){
        return Utils.pathJoin(getDataPath(), BASE_DIR);
    }

    public void uploadContents(SyncResult syncResult, DataManager dataManager) throws SeafException, InterruptedException{
        Utils.utilsLogInfo(true, "========Starting to upload CallLog...");
        synNum = 100;
        if(previous == null){
            if(synNum > 10) {
                Utils.utilsLogInfo(true, "========Clear CallLog cache...");
                dbHelper.cleanCache();
                synNum = 0;
            }else {
                ++synNum;
            }
        }
        if (ContextCompat.checkSelfPermission(adapter.getContext(), Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED){
            Utils.utilsLogInfo(true, "Read Call Log permission is not granted.");
            return;
        }
        Cursor cursor = previous;
        if(cursor == null) {
            cursor = adapter.getContentResolver().query(CallLog.Calls.CONTENT_URI,
                    null,
                    null,
                    null,
                    CallLog.Calls.DATE + " ASC");
        }
        int fileIter = cursor.getPosition();
        int fileNum = cursor.getCount();
        Utils.utilsLogInfo(true, "========Found ["+ fileIter+"/"+fileNum+"] CallLogs ========");
        List<File> callLogs = Lists.newArrayList();
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
                    while (!isCancelled() && cursor.getLong(cursor.getColumnIndex(CallLog.Calls.DATE)) <= timestamp) {
                        String name = cursor.getString(cursor.getColumnIndex(CallLog.Calls.CACHED_NAME));
                        String number = cursor.getString(cursor.getColumnIndex(CallLog.Calls.NUMBER));
                        long date = cursor.getLong(cursor.getColumnIndex(CallLog.Calls.DATE));
                        int type = cursor.getInt(cursor.getColumnIndex(CallLog.Calls.TYPE));
                        int duration = cursor.getInt(cursor.getColumnIndex(CallLog.Calls.DURATION));
                        Log.d(DEBUG_TAG, "readCallLog: number=" + number + ", date=" + date + ", type=" + type + ", duration=" + duration);
                        String filePath = Utils.pathJoin(repoFile.getAbsolutePath(), getDateStr(date) + "-" + number + ".json");
                        if (date < timestamp && !dbHelper.isUploaded(getAccountSignature(), filePath)) {
                            File file = generateCallLog(filePath, name, number, date, type, duration);
                            if (file != null && file.exists()) {
                                callLogs.add(file);
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
                String name = cursor.getString(cursor.getColumnIndex(CallLog.Calls.CACHED_NAME));
                String number = cursor.getString(cursor.getColumnIndex(CallLog.Calls.NUMBER));
                long date = cursor.getLong(cursor.getColumnIndex(CallLog.Calls.DATE));
                int type = cursor.getInt(cursor.getColumnIndex(CallLog.Calls.TYPE));
                int duration = cursor.getInt(cursor.getColumnIndex(CallLog.Calls.DURATION));
                Log.d(DEBUG_TAG, "readCallLog: number=" + number + ", date=" + date + ", type=" + type + ", duration=" + duration);
                String filePath = Utils.pathJoin(repoFile.getAbsolutePath(), getDateStr(date) + "(" + number + ").json");
                if (!dbHelper.isUploaded(getAccountSignature(), filePath)) {
                    File file = generateCallLog(filePath, name, number, date, type, duration);
                    if (file != null && file.exists()) {
                        callLogs.add(file);
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
        Utils.utilsLogInfo(true,"=======Have checked ["+Integer.toString(fileIter)+"/"+Integer.toString(fileNum)+"] callLogs======");
        Utils.utilsLogInfo(true,"=======waitForUploads===");
        adapter.waitForUploads();
        adapter.checkUploadResult(this, syncResult);
        if(cursor.isAfterLast()){
            cursor.close();
            previous = null;
        }else{
            previous = cursor;
        }
        for(File file:callLogs){
            if(file != null && file.exists()){
                file.delete();
            }
        }
        Utils.utilsLogInfo(true, "========End uploading CallLog...");
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

    private File generateCallLog(String path, String name, String number, long date, int type, int duration){
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("{\n");
            sb.append("\"Name\": ");
            sb.append("\"");
            sb.append(name);
            sb.append("\"");
            sb.append(", \n");
            sb.append("\"Number\": ");
            sb.append("\"");
            sb.append(number);
            sb.append("\"");
            sb.append(", \n");
            sb.append("\"TimeStamp\": ");
            sb.append(date);
            sb.append(", \n");
            sb.append("\"Date\": ");
            sb.append("\"");
            sb.append(getDateStr(date));
            sb.append("\"");
            sb.append(", \n");
            sb.append("\"CallType\": ");
            sb.append("\"");
            if(type == CallLog.Calls.INCOMING_TYPE){
                sb.append("Incoming");
            }else if(type == CallLog.Calls.OUTGOING_TYPE){
                sb.append("Outgoing");
            }else if(type == CallLog.Calls.MISSED_TYPE){
                sb.append("Missed");
            }else if(type == CallLog.Calls.VOICEMAIL_TYPE){
                sb.append("VoiceMail");
            }else if(type == CallLog.Calls.REJECTED_TYPE){
                sb.append("Rejected");
            }else if(type == CallLog.Calls.BLOCKED_TYPE){
                sb.append("Blocked");
            }else if(type == CallLog.Calls.ANSWERED_EXTERNALLY_TYPE){
                sb.append("Answered externally");
            }else{
                sb.append("Unknown");
            }
            sb.append("\"");
            sb.append(", \n");
            sb.append("\"Duration\": ");
            sb.append(duration);
            sb.append("\n");
            sb.append("}");
            FileWriter fileWriter = new FileWriter(path, false);
            fileWriter.write(sb.toString());
            fileWriter.close();
            return new File(path);
        }catch (IOException e){
            Utils.utilsLogInfo(true, "Failed to create callLog file " + path + ": " + e.toString());
            Log.d(DEBUG_TAG, "Failed to create callLog file " + path + ": " + e.toString());
        }
        return null;
    }
}
