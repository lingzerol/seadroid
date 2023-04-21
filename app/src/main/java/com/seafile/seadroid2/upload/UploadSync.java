package com.seafile.seadroid2.upload;

import android.content.SyncResult;
import android.util.Log;

import com.seafile.seadroid2.SeadroidApplication;
import com.seafile.seadroid2.SeafException;
import com.seafile.seadroid2.SettingsManager;
import com.seafile.seadroid2.account.Account;
import com.seafile.seadroid2.account.AccountManager;
import com.seafile.seadroid2.data.CacheItem;
import com.seafile.seadroid2.data.DataManager;
import com.seafile.seadroid2.data.DirentCache;
import com.seafile.seadroid2.data.SeafDirent;
import com.seafile.seadroid2.data.SyncEvent;
import com.seafile.seadroid2.upload.file.FileSync;
import com.seafile.seadroid2.util.SeafileLog;
import com.seafile.seadroid2.util.SyncStatus;
import com.seafile.seadroid2.util.Utils;

import org.greenrobot.eventbus.EventBus;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.Comparator;
import java.util.List;

public abstract class UploadSync {
    private static final String DEBUG_TAG = "UploadSync";
    protected final String BASE_DIR = SeafileLog.getSystemModel();
    private static final String CACHE_NAME = "UploadSync";
    protected int CLEAR_CACHE_BOUNDARY = 30;

    protected int syncType;
    protected UploadDBHelper dbHelper = null;
    protected UploadSyncAdapter adapter;

    protected Account mAccount = null;
    protected String repoID = null;
    protected String repoName = null;
    protected String dataPath = null;

    public UploadSync(UploadSyncAdapter adapter, int syncType){
        if(!UploadManager.checkSyncType(syncType)){
            throw new RuntimeException("Unknown syncType.");
        }
        this.syncType = syncType;
        this.adapter = adapter;
    }

    protected boolean isCancelled() {
        return adapter.isCancelled();
    }

    public void performSync(Account seafileAccount, DataManager dataManager, SyncResult syncResult){
        boolean isCurrentAccount = adapter.isCurrentAccount(seafileAccount);
        if(isCurrentAccount) {
            SeadroidApplication.getInstance().setSyncType(this.syncType);
            SeadroidApplication.getInstance().setScanUploadStatus(SyncStatus.SCANNING);
            EventBus.getDefault().post(new SyncEvent("start"));
        }
        try {
            String accountSignature = seafileAccount.getSignature();
            mAccount = seafileAccount;
            repoID = SettingsManager.instance().getCloudUploadRepoId(accountSignature);
            repoName = SettingsManager.instance().getCloudUploadRepoName(accountSignature);
            dataPath = SettingsManager.instance().getCloudUploadDataPath(accountSignature);
            dbHelper = UploadDBHelper.getInstance(UploadManager.getSyncName(this.syncType));
            if(repoID == null || repoName == null || dataPath == null){
                Log.e(DEBUG_TAG, "Sync aborted because the repository is null");
                return;
            }
            // make sure the repo exists
            if (!adapter.validateRepository(dataManager, repoID, repoName)) {
                /**
                 * this is a HARD error (see below). The user has to fix up the settings to make it
                 * work again.
                 *
                 * We do /not/ disable upload, as this might hide the problem from the user.
                 * instead we should display an error
                 */
                Log.e(DEBUG_TAG, "Sync aborted because the target repository does not exist");
                syncResult.databaseError = true;
                adapter.showNotificationRepoError();
                return;
            }

            adapter.clearTasksInProgress();
            uploadContents(syncResult, dataManager);

            if (isCancelled()) {
                Log.i(DEBUG_TAG, UploadManager.getSyncName(this.syncType) + " sync was cancelled.");
            } else {
                Log.i(DEBUG_TAG, UploadManager.getSyncName(this.syncType) + " sync finished successfully.");
            }

        } catch (SeafException e) {
            switch (e.getCode()) {
                /*
                 * here we have basically two scenarios
                 * SOFT) the error can be resolved without user interaction
                 *    --> mark syncResult as SOFT error (eg. stats.numIoExceptions) and return.
                 *    --> SyncManager will retry later
                 * HARD) the error can ONLY be resolved by the user performing actions on the device
                 *    --> mark syncResult as HARD error and give user a popup information.
                 */
                case HttpURLConnection.HTTP_UNAUTHORIZED:
                    // hard error -> we cannot recover without user interaction
                    syncResult.stats.numAuthExceptions++;
                    // Log.i(DEBUG_TAG, "sync aborted because of authentication error.", e);
                    adapter.showNotificationAuthError();
                    break;
                default:
                    syncResult.stats.numIoExceptions++;
                    // Log.i(DEBUG_TAG, "sync aborted because of IO or server-side error.", e);
                    break;
            }
            Log.e(DEBUG_TAG, "sync aborted because an seafile error", e);
            Utils.utilsLogInfo(true, "sync aborted because an seafile error: " + e.getMessage());
        } catch (Exception e) {
            Log.e(DEBUG_TAG, "sync aborted because an unknown error", e);
            Utils.utilsLogInfo(true, "sync aborted because an unknown error: " + e.getMessage());
            syncResult.stats.numParseExceptions++;
        }
        if(isCurrentAccount) {
            SeadroidApplication.getInstance().setScanUploadStatus(SyncStatus.SCAN_END);
            SettingsManager.instance().saveUploadCompletedTime(Utils.getSyncCompletedTime());
            EventBus.getDefault().post(new SyncEvent("end"));
        }
    }

    public abstract void uploadContents(SyncResult syncResult, DataManager dataManager) throws SeafException, InterruptedException, IOException;

    protected SyncCursor iterateCursor(SyncResult syncResult, DataManager dataManager, SyncCursor cursor) throws SeafException, InterruptedException{
        if(cursor == null || cursor.getCount() == 0){
            Utils.utilsLogInfo(true,"=======Empty Cursor.===");
            return null;
        }
        String bucketName = cursor.getBucketName();
        int fileIter = cursor.getPosition();
        int fileNum = cursor.getCount();

        Utils.utilsLogInfo(true, "========Found ["+ fileIter+"/"+fileNum+"] " + getMediaName() + " in bucket "+bucketName+"========");
        try {
            String cacheName = getCacheName() + "-" + getRepoID() + "-" + bucketName.replace("/", "-");
            DirentCache cache = getCache(cacheName, getRepoID(), Utils.pathJoin(getDirectoryPath(), bucketName), dataManager);
            if(cache != null) {
                int n = cache.getCount();
                boolean done = false;

                if(cursor.isBeforeFirst() && !cursor.isAfterLast()){
                    cursor.moveToNext();
                }

                for (int i = 0; i < n; ++i) {
                    if (cursor.isAfterLast()) {
                        break;
                    }
                    SeafDirent item = cache.get(i);
                    if(item == null){
                        continue;
                    }
                    while (!isCancelled()) {
                        if(cursor.getFileName() == null){
                            continue;
                        }
                        if(cursor.compareToCacheOrder(item) > 0){
                            break;
                        }
                        if (cursor.compareToCacheMore(item) > 0) {
                            File file = cursor.getFile();
                            if (file != null && file.exists() && dbHelper.isUploaded(getAccountSignature(), cursor.getFilePath(), cursor.getFileModified())) {
                                Log.d(DEBUG_TAG, "Skipping " + getMediaName() + " "  + cursor.getFilePath() + " because we have uploaded it in the past.");
                            } else {
                                if (file == null || !file.exists()) {
                                    Log.d(DEBUG_TAG, "Skipping " + getMediaName() + " " + cursor.getFilePath() + " because it doesn't exist");
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
                            Log.d(DEBUG_TAG, "===="+ getMediaName() + " " + cursor.getFilePath() + " in bucket " + bucketName + " already exists on the server. Skipping.");
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
            while (!isCancelled() && !cursor.isAfterLast()) {
                File file = cursor.getFile();
                if(file == null || !file.exists()){
                    Log.d(DEBUG_TAG, "Skipping "+ getMediaName() + " " + file + " because it doesn't exist");
                    syncResult.stats.numSkippedEntries++;
                }else if (!dbHelper.isUploaded(getAccountSignature(), file.getPath(), file.lastModified())) {
                    adapter.uploadFile(dataManager, file, getRepoID(), getRepoName(), Utils.pathJoin(getDirectoryPath(), bucketName));
                }
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
        Utils.utilsLogInfo(true,"=======Have checked ["+Integer.toString(fileIter)+"/"+Integer.toString(fileNum)+"] "+ getMediaName() +" in "+bucketName+".===");
        Utils.utilsLogInfo(true,"=======waitForUploads===");
        adapter.waitForUploads();
        adapter.checkUploadResult(this, syncResult);
        cursor.postProcess();
        if(cursor.isAfterLast()){
            return null;
        }
        return cursor;
    }

    public void cancelSync(Account account){
        UploadManager.disableAccountUploadSync(account, this.syncType);
    }

    public void markAsUploaded(File file){
        dbHelper.markAsUploaded(getAccountSignature(), file);
    }

    public Account getAccount(){
        return mAccount;
    }

    public String getAccountSignature(){
        return mAccount.getSignature();
    }

    public String getRepoID(){
        return repoID;
    }

    public String getRepoName(){
        return repoName;
    }

    public String getDataPath(){
        return dataPath;
    }

    public int getSyncType(){
        return syncType;
    }

    public String getDirectoryPath(){
        return Utils.pathJoin(getDataPath(), BASE_DIR);
    }

    protected String getCacheName(){
        return CACHE_NAME;
    }

    protected Comparator<SeafDirent> getComparator(){
        return new Comparator<SeafDirent>() {
            @Override
            public int compare(SeafDirent o1, SeafDirent o2) {
                return o1.name.compareTo(o2.name);
            }
        };
    }

    public abstract String getMediaName();

    protected DirentCache getCache(String name, String repoID, String dataPath, DataManager dataManager, Comparator<SeafDirent> comparator) throws IOException, InterruptedException, SeafException{
        Utils.utilsLogInfo(true, "=======saving " + name + " cache=========");
        List<SeafDirent> seafDirents = dataManager.getCachedDirents(repoID, dataPath);
        int timeOut = 10000; // wait up to a second
        while (seafDirents == null && timeOut > 0) {
            // Log.d(DEBUG_TAG, "waiting for transfer service");
            Thread.sleep(100);
            seafDirents = dataManager.getDirentsFromServer(repoID, dataPath);
            timeOut -= 100;
        }
        if (seafDirents == null) {
            return null;
        }
        return new DirentCache(name, seafDirents, comparator);
    }

    protected DirentCache getCache(String name, String repoID, String dataPath, DataManager dataManager) throws IOException, InterruptedException, SeafException{
        return getCache(name, repoID, dataPath, dataManager, getComparator());
    }

    protected static abstract class SyncCursor{

        public abstract String getBucketName();

        public abstract int getCount();

        public abstract int getPosition();

        public abstract boolean isBeforeFirst();

        public abstract boolean moveToNext();

        public abstract boolean isAfterLast();

        public abstract String getFilePath();

        public abstract String getFileName();

        public abstract File getFile();

        public abstract long getFileSize();

        public abstract long getFileModified();

        public void postProcess(){}

        public abstract int compareToCacheOrder(SeafDirent item);

        public abstract int compareToCacheMore(SeafDirent item);
    }
}
