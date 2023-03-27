package com.seafile.seadroid2.upload;

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
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
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
import com.seafile.seadroid2.data.SeafDirent;
import com.seafile.seadroid2.data.SeafRepo;
import com.seafile.seadroid2.transfer.TaskState;
import com.seafile.seadroid2.transfer.TransferService;
import com.seafile.seadroid2.transfer.UploadTaskInfo;
import com.seafile.seadroid2.ui.CustomNotificationBuilder;
import com.seafile.seadroid2.ui.activity.AccountsActivity;
import com.seafile.seadroid2.upload.album.AlbumSync;
import com.seafile.seadroid2.util.SyncStatus;
import com.seafile.seadroid2.util.Utils;

import org.greenrobot.eventbus.EventBus;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Sync adapter for media upload.
 * <p/>
 * This class uploads images/videos from the gallery to the configured seafile account.
 * It is not called directly, but managed by the Android Sync Manager instead.
 */
public class UploadSyncAdapter extends AbstractThreadedSyncAdapter {
    private static final String DEBUG_TAG = "UploadSyncAdapter";
    private ContentResolver contentResolver;
    private AccountManager manager;

    private List<UploadSync> syncs = Lists.newArrayList();
    private int lastSyncIndex = 0;

    /**
     * Will be set to true if the current sync has been cancelled.
     */
    private boolean cancelled = false;

    /**
     * Media files we have sent over to the TransferService. Thread-safe.
     */
    private List<Integer> tasksInProgress = new ArrayList<>();

    TransferService txService = null;

    ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            // this will run in a foreign thread!

            TransferService.TransferBinder binder = (TransferService.TransferBinder) service;
            synchronized (UploadSyncAdapter.this) {
                txService = binder.getService();
            }
            // Log.d(DEBUG_TAG, "connected to TransferService");
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            // this will run in a foreign thread!
            // Log.d(DEBUG_TAG, "disconnected from TransferService, aborting sync");

            onSyncCanceled();
            synchronized (UploadSyncAdapter.this) {
                txService = null;
            }
        }
    };

    /**
     * Set up the sync adapter
     */
    public UploadSyncAdapter(Context context) {
        /*
         * autoInitialize is set to false because we need to handle initialization
         * ourselves in performSync() (resetting the photo database).
         */
        super(context, false);

        contentResolver = context.getContentResolver();
        manager = new AccountManager(context);

        for(int syncType: UploadManager.SYNC_TPYES){
            syncs.add(getUploadSync(syncType));
        }
    }

    public UploadSync getUploadSync(int synType){
        if(synType == UploadManager.ALBUM_SYNC){
            return new AlbumSync(this);
        }
        return null;
    }

    private synchronized void startTransferService() {
        if (txService != null)
            return;

        Intent bIntent = new Intent(getContext(), TransferService.class);
        getContext().bindService(bIntent, mConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onSyncCanceled() {
        super.onSyncCanceled();
        // Log.d(DEBUG_TAG, "onPerformSync will be cancelled ");
        synchronized (this) {
            cancelled = true;
        }
    }

    public boolean isCancelled() {
        synchronized (this) {
            return cancelled;
        }
    }

    public void setCancelled(boolean cancelled) {
        synchronized (this) {
            this.cancelled = cancelled;
        }
    }



    /**
     * Check if repository on the server exists.
     *
     * @param dataManager
     * @return
     * @throws SeafException
     */
    public boolean validateRepository(DataManager dataManager, String repoID, String repoName) throws SeafException {
        List<SeafRepo> repos = dataManager.getReposFromServer();

        for (SeafRepo repo : repos) {
            if (repo.getID().equals(repoID) && repo.getName().equals(repoName))
                return true;
        }

        return false;
    }

    public void createDirectory(DataManager dataManager,String repoID, String directory) throws SeafException {
        List<String> dirNames = Lists.newArrayList();
        String parent = Utils.removeLastPathSeperator(directory);
        while(parent != null && !parent.equals(Utils.PATH_SEPERATOR) && !parent.isEmpty()){
            dirNames.add(Utils.fileNameFromPath(parent));
            parent = Utils.getParentPath(parent);
        }
        parent = "/";
        for(int i=dirNames.size()-1;i>=0;--i) {
            forceCreateDirectory(dataManager, repoID, parent, dirNames.get(i));
            dataManager.getDirentsFromServer(repoID, Utils.pathJoin(parent, dirNames.get(i)));
            parent = Utils.pathJoin(parent, dirNames.get(i));
        }
    }

    /**
     * Create all the subdirectories on the server for the buckets that are about to be uploaded.
     *
     * @param dataManager
     * @throws SeafException
     */
    public void createDirectories(DataManager dataManager, String repoID, String parent, List<String> dirNames) throws SeafException {
        // create base directory
        createDirectory(dataManager, repoID, parent);
        for (String directory: dirNames) {
            forceCreateDirectory(dataManager, repoID, parent, directory);
            dataManager.getDirentsFromServer(repoID, Utils.pathJoin(parent, directory));
        }
    }

    /**
     * Create a directory, rename a file away if necessary,
     *
     * @param dataManager
     * @param parent parent dir
     * @param dir directory to create
     * @throws SeafException
     */
    private void forceCreateDirectory(DataManager dataManager, String repoID, String parent, String dir) throws SeafException {

        List<SeafDirent> dirs = dataManager.getDirentsFromServer(repoID, parent);
        boolean found = false;
        for (SeafDirent dirent : dirs) {
            if (dirent.name.equals(dir) && dirent.isDir()) {
                found = true;
            } else if (dirent.name.equals(dir) && !dirent.isDir()) {
                // there is already a file. move it away.
                String newFilename = getContext().getString(R.string.camera_sync_rename_file, dirent.name);
                dataManager.rename(repoID,
                        Utils.pathJoin(Utils.pathJoin("/", parent), dirent.name),
                        newFilename,
                        false);
            }
        }
        if (!found)
            dataManager.createNewDir(repoID, Utils.pathJoin("/", parent), dir);
    }

    @Override
    public void onPerformSync(android.accounts.Account account,
                              Bundle extras, String authority,
                              ContentProviderClient provider,
                              SyncResult syncResult) {

        synchronized (this) {
            cancelled = false;
        }

        Account seafileAccount = manager.getSeafileAccount(account);
        DataManager dataManager = new DataManager(seafileAccount);

        boolean isCurrnetAccount = isCurrentAccount(seafileAccount);

        if (!SettingsManager.instance().checkUploadNetworkAvailable(seafileAccount.getSignature())) {
            // Log.d(DEBUG_TAG, "Not syncing because of data plan restriction.");
            // treat dataPlan abort the same way as a network connection error
            syncResult.stats.numIoExceptions++;
            if(isCurrnetAccount) {
                SeadroidApplication.getInstance().setScanUploadStatus(SyncStatus.NETWORK_UNAVAILABLE);
            }
            EventBus.getDefault().post(new SyncEvent("noNetwork"));
            return;
        }

        /**
         * this should never occur, as camera upload is supposed to be disabled once the camera upload
         * account signs out.
         */
        if (!seafileAccount.hasValidToken()) {
            // Log.d(DEBUG_TAG, "This account has no auth token. Disable camera upload.");
            syncResult.stats.numAuthExceptions++;
            UploadManager.disableAccountUpload(seafileAccount);
//            // we're logged out on this account. disable camera upload.
//            ContentResolver.cancelSync(account, UploadManager.AUTHORITY);
//            ContentResolver.setIsSyncable(account, UploadManager.AUTHORITY, 0);
            return;
        }

        try {
            startTransferService();

            // wait for TransferService to connect
            // Log.d(DEBUG_TAG, "waiting for transfer service");
            int timeout = 10000; // wait up to a second
            while (!isCancelled() && timeout > 0 && txService == null) {
                // Log.d(DEBUG_TAG, "waiting for transfer service");
                Thread.sleep(100);
                timeout -= 100;
            }

            if (txService == null) {
                Log.e(DEBUG_TAG, "TransferService did not come up in time, aborting sync");
                syncResult.delayUntil = 60;
                return;
            }

            if(syncs != null && !syncs.isEmpty()) {
                while (lastSyncIndex < syncs.size()) {
                    if(syncs.get(lastSyncIndex) != null && UploadManager.isEnableCloudUploadSync(seafileAccount, syncs.get(lastSyncIndex).getSyncType())){
                        syncs.get(lastSyncIndex).performSync(seafileAccount, dataManager, syncResult);
                    }
                    ++lastSyncIndex;
                }
                lastSyncIndex = (lastSyncIndex) % syncs.size();
            }

            if (isCancelled()) {
                 Log.i(DEBUG_TAG, "sync was cancelled.");
            } else {
                 Log.i(DEBUG_TAG, "sync finished successfully.");
            }
        } catch (Exception e) {
            Log.e(DEBUG_TAG, "sync aborted because an unknown error", e);
            Utils.utilsLogInfo(true, "sync aborted because an unknown error: " + e.getMessage());
            syncResult.stats.numParseExceptions++;
        } finally {
            if (txService != null) {

                // Log.d(DEBUG_TAG, "Cancelling remaining pending tasks (if any)");
                txService.cancelUploadTasksByIds(tasksInProgress);

                // Log.d(DEBUG_TAG, "disconnecting from TransferService");
                getContext().unbindService(mConnection);
                txService = null;
            }
        }
    }

    public void waitForUploads() throws InterruptedException {
        // Log.d(DEBUG_TAG, "wait for transfer service to finish our tasks");
        WAITLOOP: while (!isCancelled()) {
            Thread.sleep(100); // wait

            for (int id: tasksInProgress) {
                UploadTaskInfo info = txService.getUploadTaskInfo(id);
                if (info != null && (info.state == TaskState.INIT || info.state == TaskState.TRANSFERRING)) {
                    // there is still at least one task pending
                    continue  WAITLOOP;
                }
            }
            break;
        }
    }


    public void checkUploadResult(UploadSync sync, SyncResult syncResult) throws SeafException {
        for (int id: tasksInProgress) {
            UploadTaskInfo info = txService.getUploadTaskInfo(id);
            if (info == null) {
                continue;
            }
            if(info.err == null || info.state == TaskState.FAILED || info.state == TaskState.CANCELLED){
                txService.removeUploadTask(info.taskID);
                continue;
            }
            if (info.state == TaskState.FINISHED) {
                File file = new File(info.localFilePath);
                sync.markAsUploaded(file);
                syncResult.stats.numInserts++;
                txService.removeUploadTask(info.taskID);
            } else {
                continue;
            }
        }
    }

    public void uploadFile(DataManager dataManager, File file, String repoID, String repoName, String dataPath) throws SeafException {
        Log.d(DEBUG_TAG, "uploading file " + file.getName() + " to " + dataPath);
        Utils.utilsLogInfo(true,"====uploading file " + file.getName() + " to " + dataPath);
        int taskID = txService.addUploadTask(dataManager.getAccount(), repoID, repoName,
                dataPath, file.getAbsolutePath(), false, false);
        tasksInProgress.add(taskID);
    }

    public void showNotification(String title, String text, boolean alterOnce) {
        NotificationCompat.Builder mBuilder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mBuilder = new NotificationCompat.Builder(getContext(), CustomNotificationBuilder.CHANNEL_ID_ERROR);
        } else {
            mBuilder = new NotificationCompat.Builder(getContext());
        }

        mBuilder.setSmallIcon(R.drawable.icon)
                .setOnlyAlertOnce(alterOnce)
                .setContentTitle(title)
                .setContentText(text);

        // Creates an explicit intent for an Activity in your app
        Intent resultIntent = new Intent(getContext(), AccountsActivity.class);

        resultIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent dPendingIntent = PendingIntent.getActivity(getContext(),
                (int) System.currentTimeMillis(),
                resultIntent,
                0);

        mBuilder.setContentIntent(dPendingIntent);
        NotificationManager mNotificationManager =
                (NotificationManager) getContext().getSystemService(Context.NOTIFICATION_SERVICE);

        mNotificationManager.notify(0, mBuilder.build());
    }

    public void showNotificationAuthError() {
        showNotification(getContext().getString(R.string.camera_sync_notification_title_failed), getContext().getString(R.string.camera_sync_notification_auth_error_failed), true);
    }

    public void showNotificationRepoError() {
        showNotification(getContext().getString(R.string.camera_sync_notification_title_failed), getContext().getString(R.string.camera_sync_notification_repo_missing_failed), false);
    }

    public ContentResolver getContentResolver(){
        return contentResolver;
    }

    public boolean isCurrentAccount(Account account){
        if(account == null){
            return false;
        }
        AccountManager accountManager = new AccountManager(getContext());
        Account currAccount = accountManager.getCurrentAccount();
        if(account.equals(currAccount)){
            return true;
        }
        return false;
    }

    public void clearTasksInProgress(){
        tasksInProgress.clear();
    }
}


