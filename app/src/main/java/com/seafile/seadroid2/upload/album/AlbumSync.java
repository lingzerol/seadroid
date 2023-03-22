package com.seafile.seadroid2.upload.album;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ComponentName;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SyncResult;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.MediaStore;
import android.support.annotation.RequiresApi;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.google.common.collect.Lists;
import com.seafile.seadroid2.R;
import com.seafile.seadroid2.SeadroidApplication;
import com.seafile.seadroid2.SeafException;
import com.seafile.seadroid2.SettingsManager;
import com.seafile.seadroid2.account.Account;
import com.seafile.seadroid2.account.AccountManager;
import com.seafile.seadroid2.data.SyncEvent;
import com.seafile.seadroid2.data.DataManager;
import com.seafile.seadroid2.data.DirentCache;
import com.seafile.seadroid2.data.SeafDirent;
import com.seafile.seadroid2.data.SeafRepo;
import com.seafile.seadroid2.data.StorageManager;
import com.seafile.seadroid2.transfer.TaskState;
import com.seafile.seadroid2.transfer.TransferService;
import com.seafile.seadroid2.transfer.UploadTaskInfo;
import com.seafile.seadroid2.ui.CustomNotificationBuilder;
import com.seafile.seadroid2.ui.activity.AccountsActivity;
import com.seafile.seadroid2.ui.activity.SettingsActivity;
import com.seafile.seadroid2.upload.UploadManager;
import com.seafile.seadroid2.upload.UploadSync;
import com.seafile.seadroid2.upload.UploadSyncAdapter;
import com.seafile.seadroid2.util.SyncStatus;
import com.seafile.seadroid2.util.Utils;

import org.greenrobot.eventbus.EventBus;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Sync adapter for media upload.
 * <p/>
 * This class uploads images/videos from the gallery to the configured seafile account.
 * It is not called directly, but managed by the Android Sync Manager instead.
 */
public class AlbumSync extends UploadSync {
    private static final String DEBUG_TAG = "UploadSync";
    private static final String CACHE_NAME = "CameraSync";
    private final String BASE_DIR = super.BASE_DIR + "/Album";

    private LinkedList<String> leftBuckets = null;
    private MediaCursor previous = null;
    private int synNum = 0;

    public AlbumSync(UploadSyncAdapter adapter) {
        super(adapter, UploadManager.ALBUM_SYNC);
    }

    public List<String> getBucketNames(){
        return SettingsManager.instance().getAlbumUploadBucketList(getAccountSignature());
    }

    public String getDirectoryPath(){
        return Utils.pathJoin(getDataPath(), BASE_DIR);
    }

    public void uploadContents(SyncResult syncResult, DataManager dataManager) throws SeafException, InterruptedException{
        Utils.utilsLogInfo(true, "========Starting to upload buckets...");
        if((leftBuckets == null || leftBuckets.size() == 0) && previous == null){
            if(synNum > 30) {
                Utils.utilsLogInfo(true, "========Clear photo cache...");
                dbHelper.cleanPhotoCache();
                synNum = 0;
            }else {
                ++synNum;
            }
        }

        if (isCancelled()){
            Utils.utilsLogInfo(true, "========Cancel uploading========");
            return;
        }

        List<String> bucketList = getBucketNames();
        List<String> selectedBuckets = new ArrayList<>();

        if(leftBuckets != null && leftBuckets.size() > 0){
            selectedBuckets = leftBuckets;
        }else if (bucketList != null && bucketList.size() > 0) {
            selectedBuckets = bucketList;
        }

        Utils.utilsLogInfo(true, "========Traversal all phone buckets========");
        List<GalleryBucketUtils.Bucket> allBuckets = GalleryBucketUtils.getMediaBuckets(SeadroidApplication.getAppContext());
        Map<String, String> bucketNames = new HashMap<String, String>();
        for (GalleryBucketUtils.Bucket bucket : allBuckets) {
            if (bucket.isCameraBucket && (bucketList == null || bucketList.size() == 0)) {
                selectedBuckets.add(bucket.id);
            }
            bucketNames.put(bucket.id, bucket.name);
        }

        if(selectedBuckets != null && !selectedBuckets.isEmpty()) {
            List<String> directories = Lists.newArrayList();
            for (String id : selectedBuckets) {
                if (bucketNames.containsKey(id)) {
                    directories.add(bucketNames.get(id));
                }
            }
            adapter.createDirectories(dataManager, repoID, getDirectoryPath(), directories);
        }

        if(previous != null){
            Utils.utilsLogInfo(true, "========Continue uploading previous cursor========");
            previous = iterateCursor(syncResult, dataManager, previous);
            if(isCancelled()){
                Utils.utilsLogInfo(true, "========Cancel uploading========");
                return;
            }
        }

        Utils.utilsLogInfo(true, "========Copy select buckets========");
        leftBuckets = Lists.newLinkedList();
        leftBuckets.addAll(selectedBuckets);

        Utils.utilsLogInfo(true, "========Start traversal selected buckets========");
        for(String bucketID: selectedBuckets) {
            leftBuckets.removeFirst();
            String bucketName = bucketNames.get(bucketID);
            if(bucketName == null || bucketName.length() == 0){
                Utils.utilsLogInfo(true, "========Bucket "+ bucketName + " does not exist.");
                continue;
            }else{
                Utils.utilsLogInfo(true, "========Uploading "+ bucketName+"...");
            }
            String[] selectionArgs = new String[]{bucketID};
            String selection = MediaStore.Images.ImageColumns.BUCKET_ID + " = ? ";
            Cursor imageCursor = adapter.getContentResolver().query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    new String[]{
                            MediaStore.Images.Media._ID,
                            MediaStore.Images.Media.DISPLAY_NAME,
                            MediaStore.Images.Media.DATE_MODIFIED,
                            MediaStore.Images.Media.SIZE,
                            MediaStore.Images.Media.DATA,
                            MediaStore.Images.ImageColumns.BUCKET_DISPLAY_NAME
                    },
                    selection,
                    selectionArgs,
                    MediaStore.Images.ImageColumns.DISPLAY_NAME + " ASC"
            );
            Cursor videoCursor = null;
            if(SettingsManager.instance().isAlbumVideosUploadAllowed(getAccountSignature())){
                selectionArgs = new String[]{bucketID};
                selection = MediaStore.Video.VideoColumns.BUCKET_ID + " = ? ";
                videoCursor = adapter.getContentResolver().query(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        new String[]{
                                MediaStore.Video.Media._ID,
                                MediaStore.Video.Media.DISPLAY_NAME,
                                MediaStore.Video.Media.DATE_MODIFIED,
                                MediaStore.Video.Media.SIZE,
                                MediaStore.Video.Media.DATA,
                                MediaStore.Video.VideoColumns.BUCKET_DISPLAY_NAME
                        },
                        selection,
                        selectionArgs,
                        MediaStore.Video.VideoColumns.DISPLAY_NAME + " ASC"
                );
            }
            MediaCursor cursor = new MediaCursor(bucketName, imageCursor, videoCursor);
            if(cursor.getCount() > 0){
                if (cursor.getFilePath().startsWith(StorageManager.getInstance().getMediaDir().getAbsolutePath())) {
                    Log.d(DEBUG_TAG, "Skipping media "+ bucketName +" because it's part of the Seadroid cache");
                }else {
                    previous = iterateCursor(syncResult, dataManager, cursor);
                }
            }else {
                previous = null;
            }
            if(isCancelled()){
                Utils.utilsLogInfo(true, "========Cancel uploading========");
                break;
            }
        }
    }

    private MediaCursor iterateCursor(SyncResult syncResult, DataManager dataManager, MediaCursor cursor) throws SeafException, InterruptedException{
        if(cursor == null || cursor.getCount() == 0){
            Utils.utilsLogInfo(true,"=======Empty Cursor.===");
            return null;
        }
        String bucketName = cursor.getBucketName();
        int fileIter = cursor.getPosition();
        int fileNum = cursor.getCount();

        Utils.utilsLogInfo(true, "========Found ["+ fileIter+"/"+fileNum+"] images in bucket "+bucketName+"========");
        try {
            DirentCache cache = getCache(getRepoID(), getDirectoryPath(), bucketName, dataManager);
            if(cache != null) {
                int n = cache.getCount();
                boolean done = false;
                for (int i = 0; i < n; ++i) {
                    if (cursor.isAfterLast()) {
                        break;
                    }
                    SeafDirent item = cache.get(i);
                    while (cursor.getFileName().compareTo(item.name) <= 0) {
                        if (cursor.getFileName().compareTo(item.name) < 0 || item.size < cursor.getFileSize() && item.mtime < cursor.getFileModified()) {
                            File file = cursor.getFile();
                            if (file != null && file.exists() && dbHelper.isUploaded(getAccountSignature(), file.getPath(), file.lastModified())) {
                                Log.d(DEBUG_TAG, "Skipping media " + file.getPath() + " because we have uploaded it in the past.");
                            } else {
                                if (file == null || !file.exists()) {
                                    Log.d(DEBUG_TAG, "Skipping media " + file + " because it doesn't exist");
                                    syncResult.stats.numSkippedEntries++;
                                } else {
                                    adapter.uploadFile(dataManager, file, getRepoID(), getRepoName(), Utils.pathJoin(getDirectoryPath(), bucketName));
                                }
                            }
                        } else {
//                        ++uploadedNum;
//                        if(uploadedNum > leaveMeida){
//                            if(cursor.deleteFile()){
//                                Utils.utilsLogInfo(true, "====File " + cursor.getFileName() + " in bucket " + bucketName + " is deleted because it exists on the server. Skipping.");
//                            }
//                        }
                            Log.d(DEBUG_TAG, "====File " + cursor.getFilePath() + " in bucket " + bucketName + " already exists on the server. Skipping.");
                            dbHelper.markAsUploaded(getAccountSignature(), cursor.getFilePath(), cursor.getFileModified());
                        }
                        ++fileIter;
                        if (!cursor.moveToNext()) {
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
                if(done){
                    cache.delete();
                }
            }
            while (!cursor.isAfterLast() && !isCancelled()) {
                File file = cursor.getFile();
                adapter.uploadFile(dataManager, file, getRepoID(), getRepoName(), Utils.pathJoin(getDirectoryPath(), bucketName));
                ++fileIter;
                if(!cursor.moveToNext()){
                    break;
                }
            }
        }catch (IOException e){
            Log.e(DEBUG_TAG, "Failed to get cache file.", e);
            Utils.utilsLogInfo(true, "Failed to get cache file: "+ e.toString());
        }catch (Exception e){
            Utils.utilsLogInfo(true, e.toString());
        }
        Utils.utilsLogInfo(true,"=======Have checked ["+Integer.toString(fileIter)+"/"+Integer.toString(fileNum)+"] images in "+bucketName+".===");
        Utils.utilsLogInfo(true,"=======waitForUploads===");
        adapter.waitForUploads();
        adapter.checkUploadResult(this, syncResult);
        if(cursor.isAfterLast()){
            return null;
        }
        return cursor;
    }

    protected class MediaCursor{
        private Cursor imageCursor;
        private Cursor videoCursor;
        private String bucketName;
        private Cursor p;
        private Comparator<Cursor> comp;

        protected class DefaultCompartor implements Comparator<Cursor> {
            @Override
            public int compare(Cursor o1, Cursor o2) {
                if(o1 == null || o1.isAfterLast()){
                    return 1;
                }
                if(o2 == null || o2.isAfterLast()){
                    return -1;
                }
                return o1.getString(o1.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)).compareTo(o2.getString(o2.getColumnIndex(MediaStore.Video.Media.DISPLAY_NAME)));
            }
        };

        public MediaCursor(String bucketName, Cursor imageCursor, Cursor videoCursor){
            init(bucketName, imageCursor, videoCursor,  new DefaultCompartor());
        }

        public MediaCursor(String bucketName, Cursor imageCursor, Cursor videoCursor, Comparator<Cursor> comp){
            if(comp == null){
                comp = new DefaultCompartor();
            }
            init(bucketName, imageCursor, videoCursor, comp);
        }

        private Cursor initializeCursor(Cursor cursor){
            if(cursor != null && cursor.isBeforeFirst()){
                cursor.moveToNext();
            }
            if(cursor == null || cursor.isAfterLast()){
                if(cursor != null){
                    cursor.close();
                }
                return null;
            }
            return cursor;
        }

        private void init(String bucketName, Cursor imageCursor, Cursor videoCursor, Comparator<Cursor> comp){
            this.bucketName = bucketName;
            imageCursor = initializeCursor(imageCursor);
            videoCursor = initializeCursor(videoCursor);
            this.imageCursor = imageCursor;
            this.videoCursor = videoCursor;
            if(this.imageCursor == null && this.videoCursor == null){
                this.p = null;
            }else if(this.imageCursor == null){
                this.p = videoCursor;
            }else if(this.videoCursor == null){
                this.p = imageCursor;
            }else{
                if(comp.compare(imageCursor, videoCursor) <= 0){
                    this.p = imageCursor;
                }else{
                    this.p = videoCursor;
                }
            }
            this.comp = comp;
        }


        public int getCount(){
            if(imageCursor == null && videoCursor == null){
                return 0;
            }else if(imageCursor == null){
                return videoCursor.getCount();
            }else if(videoCursor == null){
                return imageCursor.getCount();
            }else{
                return imageCursor.getCount() + videoCursor.getCount();
            }
        }

        public int getPosition(){
            if(imageCursor == null && videoCursor == null){
                return 0;
            }else if(imageCursor == null){
                return videoCursor.getPosition();
            }else if(videoCursor == null){
                return imageCursor.getPosition();
            }else{
                return imageCursor.getPosition() + videoCursor.getPosition();
            }
        }

        public boolean isAfterLast(){
            return (this.imageCursor == null || this.imageCursor.isAfterLast())
                    && (this.videoCursor == null || this.videoCursor.isAfterLast());
        }

        public boolean moveToNext(){
            if(isAfterLast() || p == null){
                return false;
            }
            boolean res = p.moveToNext();
            if(comp.compare(imageCursor, videoCursor) <= 0){
                p = imageCursor;
            }else{
                p = videoCursor;
            }
            return res;
        }

        public String getFileName(){
            if(p == null){
                return "";
            }
            if(p == imageCursor){
                return p.getString(p.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME));
            }else{
                return p.getString(p.getColumnIndex(MediaStore.Video.Media.DISPLAY_NAME));
            }
        }

        public int getFileModified(){
            if(p == null){
                return -1;
            }
            if(p == imageCursor){
                return p.getInt(p.getColumnIndex(MediaStore.Images.Media.DATE_MODIFIED));
            }else{
                return p.getInt(p.getColumnIndex(MediaStore.Video.Media.DATE_MODIFIED));
            }
        }

        public File getFile(){
            String path = getFilePath();
            if(path == null || path.length() == 0){
                return null;
            }
            return new File(getFilePath());
        }

        public Uri getFileUri(){
            if(p == null){
                return Uri.EMPTY;
            }
            if(p == imageCursor){
                String id = p.getString(p.getColumnIndex(MediaStore.Images.Media._ID));
                return Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id);
            }else{
                String id = p.getString(p.getColumnIndex(MediaStore.Video.Media._ID));
                return Uri.withAppendedPath(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id);
            }
        }

        public String getFileID(){
            if(p == null){
                return "";
            }
            if(p == imageCursor){
                return p.getString(p.getColumnIndex(MediaStore.Images.Media._ID));
            }else{
                return p.getString(p.getColumnIndex(MediaStore.Video.Media._ID));
            }
        }

        public String getFilePath(){
            if(p == null){
                return "";
            }
            if(p == imageCursor){
                String id = p.getString(p.getColumnIndex(MediaStore.Images.Media._ID));
                Uri uri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id);
                if(uri == null){
                    return null;
                }
                return Utils.getRealPathFromURI(SeadroidApplication.getAppContext(), uri, "images");
            }else{
                String id = p.getString(p.getColumnIndex(MediaStore.Video.Media._ID));
                Uri uri = Uri.withAppendedPath(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id);
                if(uri == null){
                    return null;
                }
                return Utils.getRealPathFromURI(SeadroidApplication.getAppContext(), uri, "video");
            }
        }

        public int getFileSize(){
            if(p == null){
                return -1;
            }
            if(p == imageCursor){
                return p.getInt(p.getColumnIndex(MediaStore.Images.Media.SIZE));
            }else{
                return p.getInt(p.getColumnIndex(MediaStore.Video.Media.SIZE));
            }
        }

        public String getBucketName(){
            if(p == null){
                return "";
            }
            if(p.isBeforeFirst()){
                return bucketName;
            }
            if(p == imageCursor){
                return p.getString(p.getColumnIndex(MediaStore.Images.Media.BUCKET_DISPLAY_NAME));
            }else{
                return p.getString(p.getColumnIndex(MediaStore.Video.Media.BUCKET_DISPLAY_NAME));
            }
        }

        public boolean deleteFile(){
            File file = getFile();
            if(file == null || !file.exists()){
                return false;
            }
            return file.delete();
        }

        @Override
        protected void finalize() throws Throwable {
            super.finalize();
            if(imageCursor!=null) {
                imageCursor.close();
            }
            if(videoCursor!=null) {
                videoCursor.close();
            }
        }
    }

    private DirentCache getCache(String repoID, String dataPath, String bucketName, DataManager dataManager) throws IOException, InterruptedException, SeafException{
        String name = CACHE_NAME+repoID+"-"+bucketName;
        String serverPath = Utils.pathJoin(dataPath, bucketName);
//        if(DirentCache.cacheFileExists(name)){
//            return new DirentCache(name);
//        }
        Utils.utilsLogInfo(true, "=======savingRepo===");
        List<SeafDirent> seafDirents = dataManager.getCachedDirents(repoID, serverPath);
        int timeOut = 10000; // wait up to a second
        while (seafDirents == null && timeOut > 0) {
            // Log.d(DEBUG_TAG, "waiting for transfer service");
            Thread.sleep(100);
            seafDirents = dataManager.getDirentsFromServer(repoID, serverPath);
            timeOut -= 100;
        }
        if (seafDirents == null) {
            return null;
        }
        return new DirentCache(name, seafDirents, new Comparator<SeafDirent>() {
            @Override
            public int compare(SeafDirent o1, SeafDirent o2) {
                return o1.name.compareTo(o2.name);
            }
        });
    }
}


