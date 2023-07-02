package com.seafile.seadroid2.upload.callLog;

import android.Manifest;
import android.content.SyncResult;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Build;
import android.provider.CallLog;
import android.support.annotation.RequiresApi;
import android.support.v4.content.ContextCompat;
import android.util.Log;

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
import com.seafile.seadroid2.upload.album.AlbumSync;
import com.seafile.seadroid2.upload.file.FileSync;
import com.seafile.seadroid2.util.Utils;

import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

public class CallLogSync extends UploadSync {
    private static final String DEBUG_TAG = "CallLogUploadSync";
    private static final String CACHE_NAME = "CallLogSync";
    private final String BASE_DIR = super.BASE_DIR + "/CallLog";

    private CallLogCursor previous = null;
    private LinkedList<Integer> leftCallLogTypes = Lists.newLinkedList();
    private int synNum = 0;

    public CallLogSync(UploadSyncAdapter adapter) {
        super(adapter, UploadManager.CALLLOG_SYNC);
    }

    public String getDirectoryPath(){
        return Utils.pathJoin(getDataPath(), BASE_DIR);
    }

    public void uploadContents(SyncResult syncResult, DataManager dataManager) throws SeafException, InterruptedException{
        Utils.utilsLogInfo(true, "========Starting to upload CallLog...");
        if(previous == null){
            if(synNum > CLEAR_CACHE_BOUNDARY) {
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

        if(previous != null){
            Utils.utilsLogInfo(true, "========Continue uploading previous cursor========");
            previous = (CallLogCursor) iterateCursor(syncResult, dataManager, previous);
            if(isCancelled()){
                Utils.utilsLogInfo(true, "========Cancel uploading========");
                return;
            }
        }

        if (isCancelled()){
            Utils.utilsLogInfo(true, "========Cancel uploading========");
            return;
        }
        List<Integer> uploadCallLogTypes = leftCallLogTypes;
        if(uploadCallLogTypes == null || uploadCallLogTypes.isEmpty()){
            uploadCallLogTypes = Lists.newArrayList(
                    CallLog.Calls.INCOMING_TYPE,
                    CallLog.Calls.OUTGOING_TYPE,
                    CallLog.Calls.MISSED_TYPE,
                    CallLog.Calls.VOICEMAIL_TYPE,
                    CallLog.Calls.REJECTED_TYPE,
                    CallLog.Calls.BLOCKED_TYPE
            );
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1){
                uploadCallLogTypes.add(CallLog.Calls.ANSWERED_EXTERNALLY_TYPE);
            }
        }
        leftCallLogTypes = Lists.newLinkedList();
        leftCallLogTypes.addAll(uploadCallLogTypes);

        File repoFile = dataManager.getLocalRepoFile(getRepoName(), getRepoID(), getDirectoryPath());
        String repoPath = repoFile.getAbsolutePath();

        for(Integer callType: uploadCallLogTypes){
            leftCallLogTypes.removeFirst();
            adapter.createDirectory(dataManager, getRepoID(), Utils.pathJoin(getDirectoryPath(), getCallLogTypeName(callType)));

            Cursor cursor = adapter.getContentResolver().query(CallLog.Calls.CONTENT_URI,
                    null,
                    CallLog.Calls.TYPE + " = ? ",
                    new String[]{String.valueOf(callType)},
                    CallLog.Calls.DATE + " ASC");
            CallLogCursor callLogCursor = new CallLogCursor(repoPath, getCallLogTypeName(callType), cursor);
            if(cursor.getCount() > 0) {
                previous = (CallLogCursor) iterateCursor(syncResult, dataManager, callLogCursor);
            }else{
                Utils.utilsLogInfo(true, "=======CallLog Sync: the call type " + getCallLogTypeName(callType) + " is cancelled because the directory is empty");
                previous = null;
            }
            if(isCancelled()){
                Utils.utilsLogInfo(true, "========Cancel uploading========");
                break;
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

    @Override
    public String getMediaName() {
        return "CallLog";
    }

    private String getCallLogTypeName(int type){
        if(type == CallLog.Calls.INCOMING_TYPE){
            return "Incoming";
        }else if(type == CallLog.Calls.OUTGOING_TYPE){
            return "Outgoing";
        }else if(type == CallLog.Calls.MISSED_TYPE){
            return "Missed";
        }else if(type == CallLog.Calls.VOICEMAIL_TYPE){
            return "VoiceMail";
        }else if(type == CallLog.Calls.REJECTED_TYPE){
            return "Rejected";
        }else if(type == CallLog.Calls.BLOCKED_TYPE){
            return "Blocked";
        }else if(type == CallLog.Calls.ANSWERED_EXTERNALLY_TYPE){
            return "Answered externally";
        }
        return "Unknown";
    }

    private File generateCallLog(String path, JSONObject jsonObject, long timestamp, int type){
        if(jsonObject == null){
            return null;
        }
        try {
            jsonObject.put("Date", getDateStr(timestamp));
            jsonObject.put("CallTypeName", getCallLogTypeName(type));
            FileWriter fileWriter = new FileWriter(path, false);
            fileWriter.write(jsonObject.toString(4));
            fileWriter.close();
            return new File(path);
        }catch (Exception e){
            Utils.utilsLogInfo(true, "Failed to create callLog file " + path + ": " + e.toString());
            Log.d(DEBUG_TAG, "Failed to create callLog file " + path + ": " + e.toString());
        }
        return null;
    }

    private class CallLogCursor extends UploadSync.SyncCursor{
        private String repoPath = null;
        private String bucketName = null;
        private Cursor cursor = null;
        private List<File> localFiles = Lists.newArrayList();

        public CallLogCursor(String repoPath, String bucketName, Cursor cursor){
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
            String number = cursor.getString(cursor.getColumnIndex(CallLog.Calls.NUMBER));
            long date = cursor.getLong(cursor.getColumnIndex(CallLog.Calls.DATE));
            return getDateStr(date) + "(" + number + ").json";
        }

        public File getFile(){
            if(cursor == null){
                return null;
            }
            String filePath = getFilePath();
            if(filePath == null){
                return null;
            }
            long timestamp = cursor.getLong(cursor.getColumnIndex(CallLog.Calls.DATE));
            int type = cursor.getInt(cursor.getColumnIndex(CallLog.Calls.TYPE));
            JSONObject jsonObject = UploadSync.SyncCursor.ToJson(cursor);
            if(jsonObject == null){
                return null;
            }
            File file = generateCallLog(filePath, jsonObject, timestamp, type);
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
