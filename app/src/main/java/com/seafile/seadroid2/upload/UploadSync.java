package com.seafile.seadroid2.upload;

import android.content.SyncResult;
import android.util.Log;

import com.seafile.seadroid2.SeadroidApplication;
import com.seafile.seadroid2.SeafException;
import com.seafile.seadroid2.SettingsManager;
import com.seafile.seadroid2.account.Account;
import com.seafile.seadroid2.account.AccountManager;
import com.seafile.seadroid2.data.DataManager;
import com.seafile.seadroid2.data.SyncEvent;
import com.seafile.seadroid2.util.SyncStatus;
import com.seafile.seadroid2.util.Utils;

import org.greenrobot.eventbus.EventBus;

import java.io.File;
import java.net.HttpURLConnection;
import java.util.List;

public abstract class UploadSync {
    private static final String DEBUG_TAG = "UploadSync";
    protected final String BASE_DIR = "Back_Up";

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

    public abstract void uploadContents(SyncResult syncResult, DataManager dataManager) throws SeafException, InterruptedException;

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
}
