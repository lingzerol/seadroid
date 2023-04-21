package com.seafile.seadroid2.upload.file;

import android.Manifest;
import android.content.SyncResult;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v4.content.ContextCompat;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;

import com.google.common.collect.Lists;
import com.seafile.seadroid2.SeadroidApplication;
import com.seafile.seadroid2.SeafException;
import com.seafile.seadroid2.SettingsManager;
import com.seafile.seadroid2.data.CacheItem;
import com.seafile.seadroid2.data.DataManager;
import com.seafile.seadroid2.data.DirentCache;
import com.seafile.seadroid2.data.SeafDirent;
import com.seafile.seadroid2.data.StorageManager;
import com.seafile.seadroid2.upload.UploadManager;
import com.seafile.seadroid2.upload.UploadSync;
import com.seafile.seadroid2.upload.UploadSyncAdapter;
import com.seafile.seadroid2.upload.file.FileUploadCacheDBHelper;
import com.seafile.seadroid2.util.Utils;

import java.io.File;
import java.io.IOException;
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
public class FileSync extends UploadSync {
    private static final String DEBUG_TAG = "FileUploadSync";
    private static final String CACHE_NAME = "FileSync";
    private final String BASE_DIR = super.BASE_DIR + "/File";

    private int synNum = 0;
    private LinkedList<Pair<String, String>> leftPaths = null;
    private FileIterator previous = null;

    private FileUploadCacheDBHelper fileDBHelper = FileUploadCacheDBHelper.getInstance();

    public FileSync(UploadSyncAdapter adapter) {
        super(adapter, UploadManager.FILE_SYNC);
    }

    public List<String> getBucketNames(){
        return SettingsManager.instance().getAlbumUploadBucketList(getAccountSignature());
    }

    public String getDirectoryPath(){
        return Utils.pathJoin(getDataPath(), BASE_DIR);
    }

    public void uploadContents(SyncResult syncResult, DataManager dataManager) throws SeafException, InterruptedException, IOException{
        Utils.utilsLogInfo(true, "========Starting to upload files...");
        if((leftPaths == null || leftPaths.size() == 0) && previous == null){
            if(synNum > CLEAR_CACHE_BOUNDARY) {
                Utils.utilsLogInfo(true, "========Clear file cache...");
                dbHelper.cleanCache();
                synNum = 0;
            }else {
                ++synNum;
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Utils.utilsLogInfo(true, "External Storage Manager permission is not granted.");
                return;
            }
        }

        if(previous != null){
            Utils.utilsLogInfo(true, "========Continue uploading previous cache========");
            previous = (FileIterator) iterateCursor(syncResult, dataManager, previous);
            if(isCancelled()){
                Utils.utilsLogInfo(true, "========Cancel uploading========");
                return;
            }
        }

        if (isCancelled()){
            Utils.utilsLogInfo(true, "========Cancel uploading========");
            return;
        }

        List<Pair<String, String>> uploadPaths = leftPaths;
        if(uploadPaths == null || uploadPaths.isEmpty()){
            List<String> targetUploadPaths = SettingsManager.instance().getFileUploadDirs(getAccountSignature());
            uploadPaths = Lists.newArrayList();
            for(String path: targetUploadPaths){
                String remoteDirPath = fileDBHelper.getUploadDirName(getAccountSignature(), path);
                File targetDir = new File(path);
                if(!targetDir.exists()){
                    Utils.utilsLogInfo(true, "=======File Sync: the path " + targetDir + " is cancelled because the directory is not exists");
                    continue;
                }
                uploadPaths.add(new Pair<String, String>(remoteDirPath, path));
                List<Pair<String, String>> subDirs = getAllSubDirPairs(remoteDirPath, path);
                if(subDirs != null && !subDirs.isEmpty()){
                    uploadPaths.addAll(subDirs);
                }
            }
        }

        leftPaths = Lists.newLinkedList();
        leftPaths.addAll(uploadPaths);

        for(Pair<String, String> pathPair: uploadPaths){
            leftPaths.removeFirst();
            if(pathPair == null){
                continue;
            }
            String remotePath = pathPair.first;
            String localPath = pathPair.second;
            File localDir = new File(localPath);
            if(!localDir.exists()){
                Utils.utilsLogInfo(true, "=======File Sync: the path " + localPath + " is skipped because the directory is not exists");
                continue;
            }
            adapter.createDirectory(dataManager, getRepoID(), Utils.pathJoin(getDirectoryPath(), remotePath));
            CacheItem<String> cache = getLocalFileCache(localPath, new Comparator<String>() {
                @Override
                public int compare(String o1, String o2) {
                    if(o1 == null && o2 == null){
                        return 0;
                    }
                    if(o1 == null){
                        return -1;
                    }
                    if(o2 == null){
                        return 1;
                    }
                    return Utils.fileNameFromPath(Utils.removeLastPathSeperator(o1)).compareTo(Utils.fileNameFromPath(Utils.removeLastPathSeperator(o2)));
                }
            });
            FileIterator cursor = new FileIterator(remotePath, cache);
            if(cursor.getCount() > 0) {
                previous = (FileIterator) iterateCursor(syncResult, dataManager, cursor);
            }else{
                Utils.utilsLogInfo(true, "=======File Sync: the path " + localPath + " is cancelled because the directory is empty");
                previous = null;
            }
            if(isCancelled()){
                Utils.utilsLogInfo(true, "========Cancel uploading========");
                break;
            }
        }
        Utils.utilsLogInfo(true, "========End uploading files...");
    }


    public String getMediaName(){
        return "file";
    }

    public CacheItem<String> getLocalFileCache(String path, Comparator<String> comparator)throws IOException{
        File file = new File(path);
        if(!file.exists()){
            return null;
        }
        File[] subFiles = file.listFiles();
//        Utils.utilsLogInfo(true, TextUtils.join(",", subFiles));
        List<String> files = Lists.newArrayList();
        if(subFiles != null && subFiles.length > 0) {
            for (File subFile : subFiles) {
                if (subFile != null && subFile.isFile()) {
                    files.add(subFile.getAbsolutePath());
                }
            }
        }else{
            return null;
        }

//        Utils.utilsLogInfo(true, "cache" + TextUtils.join(",", files));
        CacheItem<String> cache = new CacheItem<String>(CACHE_NAME+"-"+path.replace("/", "-"), files, comparator) {
            @Override
            protected byte[] toBytes(String item) {
                return item.getBytes();
            }

            @Override
            protected String fromBytes(byte[] bytes) {
                return new String(bytes);
            }
        };
        return cache;
    }

    public List<Pair<String, String>> getAllSubDirPairs(String remotePath, String path){
        if(path == null){
            return null;
        }
        File file = new File(path);
        if(!file.exists()){
            return null;
        }
        File[] subFiles = file.listFiles();
        List<Pair<String, String>> res = Lists.newArrayList();
        for(File subFile: subFiles){
            if(subFile != null && subFile.exists() && subFile.isDirectory()){
                String subRemotePath = Utils.pathJoin(remotePath, subFile.getName());
                res.add(new Pair<String, String>(subRemotePath, subFile.getAbsolutePath()));
                List<Pair<String, String>> subDirs = getAllSubDirPairs(subRemotePath, subFile.getPath());
                if(subDirs != null && !subDirs.isEmpty()){
                    res.addAll(subDirs);
                }
            }
        }
        return res;
    }

    private static class FileIterator extends UploadSync.SyncCursor{
        private final String bucketName;
        private final CacheItem<String> cache;
        private int index = 0;
        public FileIterator(String bucketName, CacheItem<String> cache){
            super();
            this.bucketName = bucketName;
            this.cache = cache;
            this.index = 0;
        }

        public String getBucketName(){
            return bucketName;
        }

        public int getCount(){
            if(cache != null){
                return cache.getCount();
            }
            return 0;
        }

        public int getPosition(){
            if(cache != null){
                return index;
            }
            return 0;
        }

        public boolean isBeforeFirst(){
            return false;
        }

        public boolean moveToNext(){
            if(cache != null){
                ++index;
                return index < getCount();
            }
            return false;
        }

        public boolean isAfterLast(){
            if(cache != null){
                return index >= getCount();
            }
            return true;
        }

        public String getFilePath(){
            if(cache != null && index < getCount()){
                return cache.get(index);
            }
            return null;
        }

        public String getFileName(){
            String filePath = getFilePath();
            if(filePath != null){
                return Utils.fileNameFromPath(Utils.removeLastPathSeperator(filePath));
            }
            return null;
        }

        public File getFile(){
            String filePath = getFilePath();
            if(filePath != null){
                return new File(filePath);
            }
            return null;
        }

        public long getFileSize(){
            File file = getFile();
            if(file != null && file.exists()){
                return file.length();
            }
            return 0;
        }

        public long getFileModified(){
            File file = getFile();
            if(file != null && file.exists()){
                return file.lastModified();
            }
            return 0;
        }

        public int compareToCacheOrder(SeafDirent item){
            if(item == null){
                return 1;
            }
            String fileName = getFileName();
            if(fileName == null){
                return -1;
            }
            return fileName.compareTo(item.name);
        }

        public int compareToCacheMore(SeafDirent item){
            if(item == null){
                return 1;
            }
            return (compareToCacheOrder(item) < 0 || item.size < getFileSize() && item.mtime < getFileModified())?1:0;
        }

        @Override
        protected void finalize() throws Throwable {
            super.finalize();
            if(cache != null) {
                cache.delete();
            }
        }
    }
}


