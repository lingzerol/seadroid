package com.seafile.seadroid2.upload;

import android.content.ContentResolver;
import android.content.Context;
import android.os.Bundle;

import com.seafile.seadroid2.BuildConfig;
import com.seafile.seadroid2.SettingsManager;
import com.seafile.seadroid2.account.Account;
import com.seafile.seadroid2.account.AccountManager;


/**
 * Camera Upload Manager.
 * <p/>
 * This class can be used by other parts of Seadroid to enable/configure the camera upload
 * service.
 */
public class UploadManager {
    public static final String AUTHORITY = BuildConfig.APPLICATION_ID + ".upload.provider";
    public static String ALLOW_VIDEO = "video";
    public static String ALLOW_CUSTOM_ALBUM = "custom_album";

    public static final int ANY_SYNC = (~0);
    public static final int ALBUM_SYNC = 1;
    public static final int FILE_SYNC = 2;
    public static final int CALLLOG_SYNC = 4;
    public static final int BROWSER_RECORD_SYNC = 8;
    public static final int SYNC_TPYES[] = {ALBUM_SYNC, FILE_SYNC, CALLLOG_SYNC, BROWSER_RECORD_SYNC};


    private AccountManager accountManager;

    public UploadManager(Context context) {
        accountManager = new AccountManager(context);
    }

    public static boolean checkSyncType(int syncType){
        if(syncType == ANY_SYNC || syncType == ALBUM_SYNC || syncType == FILE_SYNC || syncType == CALLLOG_SYNC || syncType == BROWSER_RECORD_SYNC){
            return true;
        }
        return false;
    }

    public static String getSyncName(int syncType){
        if(syncType == ALBUM_SYNC){
            return "Album";
        }else if(syncType == FILE_SYNC){
            return "File";
        }else if (syncType == CALLLOG_SYNC){
            return "CallLog";
        }else if(syncType == BROWSER_RECORD_SYNC){
            return "BrowserRecord";
        }
        return "";
    }

    public static boolean isEnableCloudUpload(Account account) {
        if(account == null){
            return false;
        }
        return SettingsManager.instance().getCloudUploadEnable(account.getSignature());
    }

    public static boolean isEnableCloudUploadSync(Account account, int syncType){
        if(account == null || !checkSyncType(syncType)){
            return false;
        }
        return SettingsManager.instance().isCloudUploadSyncAllowed(account.getSignature(), syncType);
    }

    public static boolean isSyncable(Account account) {
        if(account == null){
            return false;
        }
        if(isEnableCloudUpload(account) && isEnableCloudUploadSync(account, ANY_SYNC)){
            return ContentResolver.getIsSyncable(account.getAndroidAccount(), AUTHORITY) > 0;
        }else {
            ContentResolver.cancelSync(account.getAndroidAccount(), AUTHORITY);
            ContentResolver.setIsSyncable(account.getAndroidAccount(), AUTHORITY, 0);
        }
        return false;
    }

    /**
     * Initiate a camera sync immediately.
     */
    public static void performSync(Account cameraAccount) {
        if (cameraAccount != null){
            ContentResolver.requestSync(cameraAccount.getAndroidAccount(), AUTHORITY, Bundle.EMPTY);
        }
    }

    /**
     * Initiate a camera sync immediately, upload all media files again.
     */
    public static void performFullSync(Account cameraAccount) {
        Bundle b = new Bundle();
        b.putBoolean(ContentResolver.SYNC_EXTRAS_INITIALIZE, true);

        if (cameraAccount != null) {
            ContentResolver.requestSync(cameraAccount.getAndroidAccount(), AUTHORITY, b);
        }
    }

    public static void enableAccountUpload(Account account) {
        if(account == null){
            return;
        }
        SettingsManager.instance().saveCloudUploadEnable(account.getSignature(), true);
        if(isEnableCloudUploadSync(account, ANY_SYNC)) {
            ContentResolver.setIsSyncable(account.getAndroidAccount(), AUTHORITY, 1);
            ContentResolver.setSyncAutomatically(account.getAndroidAccount(), AUTHORITY, true);
        }
    }

    public static void enableAccountUploadSync(Account account, int syncType){
        if(account == null){
            return;
        }
        SettingsManager.instance().saveCloudUploadSyncAllowed(account.getSignature(), syncType, true);
        if(isEnableCloudUpload(account) && !isSyncable(account)){
            ContentResolver.setIsSyncable(account.getAndroidAccount(), AUTHORITY, 1);
            ContentResolver.setSyncAutomatically(account.getAndroidAccount(), AUTHORITY, true);
        }
    }


    public void disableAllUpload() {
        for (Account account : accountManager.getAccountList()) {
            SettingsManager.instance().saveCloudUploadEnable(account.getSignature(), false);
            ContentResolver.cancelSync(account.getAndroidAccount(), AUTHORITY);
            ContentResolver.setIsSyncable(account.getAndroidAccount(), AUTHORITY, 0);
        }
    }

    public static void disableAccountUpload(Account account) {
        if(account == null){
            return;
        }
        SettingsManager.instance().saveCloudUploadEnable(account.getSignature(), false);
        ContentResolver.cancelSync(account.getAndroidAccount(), AUTHORITY);
        ContentResolver.setIsSyncable(account.getAndroidAccount(), AUTHORITY, 0);
    }

    public static void disableAccountUploadSync(Account account, int syncType) {
        if (account == null) {
            return;
        }
        SettingsManager.instance().saveCloudUploadSyncAllowed(account.getSignature(), syncType, false);
        if(!isEnableCloudUploadSync(account, ANY_SYNC)){
            ContentResolver.cancelSync(account.getAndroidAccount(), AUTHORITY);
            ContentResolver.setIsSyncable(account.getAndroidAccount(), AUTHORITY, 0);
        }
    }
}
