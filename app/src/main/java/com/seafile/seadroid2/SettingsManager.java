package com.seafile.seadroid2;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.text.TextUtils;

import com.seafile.seadroid2.account.Account;
import com.seafile.seadroid2.account.AccountManager;
import com.seafile.seadroid2.gesturelock.LockPatternUtils;
import com.seafile.seadroid2.loopimages.DirInfo;
import com.seafile.seadroid2.util.Utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Access the app settings
 */
public final class SettingsManager {
    private static final String DEBUG_TAG = "SettingsManager";

    private static SettingsManager instance;
    private static SharedPreferences settingsSharedPref;
    private static SharedPreferences sharedPref;
    private static SharedPreferences.Editor editor;

    private SettingsManager() {
        if (SeadroidApplication.getAppContext() != null) {
            settingsSharedPref = PreferenceManager.getDefaultSharedPreferences(SeadroidApplication.getAppContext());
            sharedPref = SeadroidApplication.getAppContext().getSharedPreferences(AccountManager.SHARED_PREF_NAME, Context.MODE_PRIVATE);
            editor = sharedPref.edit();
        }
    }


    // Account
    public static final String SETTINGS_ACCOUNT_INFO_KEY = "account_info_user_key";
    public static final String SETTINGS_ACCOUNT_SPACE_KEY = "account_info_space_key";
    public static final String SETTINGS_ACCOUNT_SIGN_OUT_KEY = "account_sign_out_key";

    // privacy category
    public static final String PRIVACY_CATEGORY_KEY = "category_privacy_key";

    // Client side encryption
    public static final String CLIENT_ENC_SWITCH_KEY = "client_encrypt_switch_key";
    public static final String CLEAR_PASSOWR_SWITCH_KEY = "clear_password_switch_key";
    public static final String AUTO_CLEAR_PASSOWR_SWITCH_KEY = "auto_clear_password_switch_key";

    // Gesture Lock
    public static final String GESTURE_LOCK_SWITCH_KEY = "gesture_lock_switch_key";
    public static final String GESTURE_LOCK_KEY = "gesture_lock_key";
    public static final int GESTURE_LOCK_REQUEST = 1;

    // Camera upload
    public static final String PKG = "com.seafile.seadroid2";

    public static final String SHARED_PREF_CONTACTS_UPLOAD_REPO_ID = PKG + ".contacts.repoid";
    public static final String SHARED_PREF_CONTACTS_UPLOAD_REPO_NAME = PKG + ".contacts.repoName";

    public static final String SHARED_PREF_STORAGE_DIR = PKG + ".storageId";

    public static final String SHARED_PREF_CLOUD_UPLOAD_REPO_ID = PKG + ".upload.repoid";
    public static final String SHARED_PREF_CLOUD_UPLOAD_REPO_NAME = PKG + ".upload.repoName";
    public static final String SHARED_PREF_CLOUD_UPLOAD_DATA_PATH = PKG + ".upload.dataPath";
    public static final String SHARED_PREF_ALBUM_UPLOAD_BUCKETS = PKG + ".upload.album.buckets";

//    public static final String SHARED_PREF_CAMERA_UPLOAD_ACCOUNT_EMAIL = PKG + ".camera.account.email";
//    public static final String SHARED_PREF_CAMERA_UPLOAD_ACCOUNT_NAME = PKG + ".camera.account.name";
//    public static final String SHARED_PREF_CAMERA_UPLOAD_ACCOUNT_SERVER = PKG + ".camera.account.server";
//    public static final String SHARED_PREF_CAMERA_UPLOAD_ACCOUNT_TOKEN = PKG + ".camera.account.token";
//    public static final String CAMERA_UPLOAD_SWITCH_KEY = "camera_upload_switch_key";
//    public static final String CAMERA_UPLOAD_REPO_KEY = "camera_upload_repo_key";
//    public static final String CAMERA_UPLOAD_ADVANCED_SCREEN_KEY = "screen_camera_upload_advanced_feature";
//    public static final String CAMERA_UPLOAD_ADVANCED_CATEGORY_KEY = "category_camera_upload_advanced_key";
//    public static final String CAMERA_UPLOAD_ALLOW_DATA_PLAN_SWITCH_KEY = "allow_data_plan_switch_key";
//    public static final String CAMERA_UPLOAD_ALLOW_VIDEOS_SWITCH_KEY = "allow_videos_upload_switch_key";
//    public static final String CAMERA_UPLOAD_BUCKETS_KEY = "camera_upload_buckets_key";
//    public static final String CAMERA_UPLOAD_CATEGORY_KEY = "category_camera_upload_key";
//    public static final String CAMERA_UPLOAD_CUSTOM_BUCKETS_KEY = "camera_upload_buckets_switch_key";

//    public static final String CAMERA_UPLOAD_STATE = "camera_upload_state";
    public static final String CLOUD_UPLOAD_CATEGORY_KEY = "category_cloud_upload_key";
    public static final String CLOUD_UPLOAD_SWITCH_KEY = "cloud_upload_switch_key";
    public static final String CLOUD_UPLOAD_REPO_KEY = "cloud_upload_repo_key";
    public static final String CLOUD_UPLOAD_ADVANCED_SCREEN_KEY = "screen_upload_advanced_feature";
    public static final String CLOUD_UPLOAD_ADVANCED_CATEGORY_KEY = "category_upload_advanced_key";
    public static final String CLOUD_UPLOAD_ALLOW_DATA_PLAN_SWITCH_KEY = "allow_data_plan_switch_key";
    public static final String CLOUD_UPLOAD_SYNC_TYPE_SWITCH_KEY = "cloud_upload_sync_type_switch_key";
    public static final String ALBUM_UPLOAD_SWITCH_KEY = "album_upload_switch_key";
    public static final String ALBUM_UPLOAD_ADVANCED_SCREEN_KEY = "album_upload_advanced_feature";
    public static final String ALBUM_UPLOAD_ADVANCED_CATEGORY_KEY = "category_album_upload_advanced_key";
    public static final String ALBUM_UPLOAD_ALLOW_VIDEOS_SWITCH_KEY = "allow_videos_upload_switch_key";
    public static final String ALBUM_UPLOAD_CUSTOM_BUCKETS_KEY = "album_upload_buckets_switch_key";
    public static final String ALBUM_UPLOAD_BUCKETS_KEY = "album_upload_buckets_key";
    public static final String CLOUD_UPLOAD_STATE = "cloud_upload_state";
    public static final String CALLLOG_UPLOAD_SWITCH_KEY = "calllog_upload_switch_key";

    //contacts
    public static final String CONTACTS_UPLOAD_CATEGORY_KEY = "category_contacts_upload_key";
    public static final String CONTACTS_UPLOAD_SWITCH_KEY = "contacts_upload_switch_key";
    public static final String SETTINGS_ABOUT_VERSION_KEY = "settings_about_version_key";
    public static final String SETTINGS_ABOUT_AUTHOR_KEY = "settings_about_author_key";
    public static final String SETTINGS_PRIVACY_POLICY_KEY = "settings_privacy_policy_key";
    public static final String CONTACTS_UPLOAD_REPO_KEY = "contacts_upload_repo_key";
    public static final String CONTACTS_UPLOAD_REPO_TIME_KEY = "contacts_upload_repo_time_key";
    public static final String CONTACTS_UPLOAD_REPO_BACKUP_KEY = "contacts_upload_repo_backup_key";
    public static final String CONTACTS_UPLOAD_REPO_RECOVERY_KEY = "contacts_upload_repo_recovery_key";

    // Cache
    public static final String SETTINGS_CACHE_CATEGORY_KEY = "settings_cache_key";
    public static final String SETTINGS_CACHE_SIZE_KEY = "settings_cache_info_key";
    public static final String SETTINGS_CLEAR_CACHE_KEY = "settings_clear_cache_key";
    public static final String SETTINGS_CACHE_DIR_KEY = "settings_cache_location_key";

    // Sort files
    public static final String SORT_FILES_TYPE = "sort_files_type";
    public static final String SORT_FILES_ORDER = "sort_files_order";

    //CameraSyncStatus
    public static final String WAITING_UPLOAD_NUMBER = "waiting_upload_number";
    public static final String TOTAL_UPLOAD_NUMBER = "total_upload_number";
    public static final String PIC_CHECK_START = "pic_check_start";
    public static final String UPLOAD_COMPLETED_TIME = "upload_completed_time";

    // Loop Images Widget
    public static final String LOOPIMAGES_REMOTE_LIBRARY_DATAPLAN = PKG + ".loopimages.remote_library_data_plan_";
    public static final String LOOPIMAGES_REMOTE_LIBRARY_DIRINFO = PKG + ".loopimages.remote_library_dir_info_";

    public static long lock_timestamp = 0;
    public static final long LOCK_EXPIRATION_MSECS = 5 * 60 * 1000;

    public static final String PRIVACY_POLICY_CONFIRMED = "privacy_policy_confirmed";

    public static SettingsManager instance() {
        if (instance == null) {
            synchronized (SettingsManager.class) {
                if (instance == null) {
                    instance = new SettingsManager();
                }
            }
        }

        if (settingsSharedPref == null) {
            settingsSharedPref = PreferenceManager.getDefaultSharedPreferences(SeadroidApplication.getAppContext());
        }
        if (sharedPref == null) {
            sharedPref = SeadroidApplication.getAppContext().getSharedPreferences(AccountManager.SHARED_PREF_NAME, Context.MODE_PRIVATE);
            editor = sharedPref.edit();
        }
        return instance;
    }


    public void registerSharedPreferencesListener(SharedPreferences.OnSharedPreferenceChangeListener listener) {
        settingsSharedPref.registerOnSharedPreferenceChangeListener(listener);
        sharedPref.registerOnSharedPreferenceChangeListener(listener);
    }

    public void unregisterSharedPreferencesListener(SharedPreferences.OnSharedPreferenceChangeListener listener) {
        settingsSharedPref.unregisterOnSharedPreferenceChangeListener(listener);
        sharedPref.unregisterOnSharedPreferenceChangeListener(listener);
    }

    /**
     * Client side encryption only support for encrypted library
     */
    public void setupEncrypt(boolean enable) {
        settingsSharedPref.edit().putBoolean(CLIENT_ENC_SWITCH_KEY, enable)
                .commit();
    }

    /**
     * Whether the user has enabled client side encryption
     */
    public boolean isEncryptEnabled() {
        return settingsSharedPref.getBoolean(CLIENT_ENC_SWITCH_KEY, false);
    }

    /**
     * Auto clear password
     */
    public void setupPasswordAutoClear(boolean enable) {
        settingsSharedPref.edit().putBoolean(AUTO_CLEAR_PASSOWR_SWITCH_KEY, enable)
                .commit();
    }

    /**
     * Whether the user has enabled password auto clear when logout account
     */
    public boolean isPasswordAutoClearEnabled() {
        return settingsSharedPref.getBoolean(AUTO_CLEAR_PASSOWR_SWITCH_KEY, false);
    }

    public void setupGestureLock() {
        settingsSharedPref.edit().putBoolean(GESTURE_LOCK_SWITCH_KEY, true)
                .commit();
        saveGestureLockTimeStamp();
    }

    /**
     * Whether the user has setup a gesture lock or not
     */
    public boolean isGestureLockEnabled() {
        return settingsSharedPref.getBoolean(GESTURE_LOCK_SWITCH_KEY, false);
    }

    /**
     * For convenience, if the user has given the correct gesture lock, he
     * would not be asked for gesture lock for a short period of time.
     */
    public boolean isGestureLockRequired() {
        if (!isGestureLockEnabled()) {
            return false;
        }
        LockPatternUtils mLockPatternUtils = new LockPatternUtils(SeadroidApplication.getAppContext());
        if (!mLockPatternUtils.savedPatternExists()) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (now < lock_timestamp + LOCK_EXPIRATION_MSECS) {
            return false;
        }

        return true;
    }

    public void saveGestureLockTimeStamp() {
        lock_timestamp = System.currentTimeMillis();
    }


    public void saveLoopImagesWidgetDataPlanAllowed(int appWidgetId, boolean isAllowed){
        settingsSharedPref.edit().putBoolean(LOOPIMAGES_REMOTE_LIBRARY_DATAPLAN + appWidgetId, isAllowed).commit();
    }

    public boolean getLoopImagesWidgetDataPlanAllowed(int appWidgetId){
        return settingsSharedPref.getBoolean(LOOPIMAGES_REMOTE_LIBRARY_DATAPLAN + appWidgetId, false);
    }

    public void setLoopImagesWidgetDirInfo(int appWidgetId, List<String> info) {
        String dirInfo = TextUtils.join(";", info);
        settingsSharedPref.edit().putString(LOOPIMAGES_REMOTE_LIBRARY_DIRINFO + appWidgetId, dirInfo).commit();
    }

    public List<String> getLoopImagesWidgetDirInfo(int appWidgetId){
        String dirInfoStr = settingsSharedPref.getString(LOOPIMAGES_REMOTE_LIBRARY_DIRINFO + appWidgetId, "");
        List<String> res = Arrays.asList(TextUtils.split(dirInfoStr, ";"));
        return res;
    }

    public void deleteLoopImagesWidgetInfo(int appWidgetId) {
        if(settingsSharedPref.contains(LOOPIMAGES_REMOTE_LIBRARY_DIRINFO)){
            settingsSharedPref.edit().remove(LOOPIMAGES_REMOTE_LIBRARY_DIRINFO + appWidgetId).commit();
        }
    }

    public boolean getCloudUploadEnable(String accountSignature) {
        return settingsSharedPref.getBoolean(CLOUD_UPLOAD_SWITCH_KEY + "-" + accountSignature, false);
    }

    public void saveCloudUploadEnable(String accountSignature, boolean isEnabled) {
        settingsSharedPref.edit().putBoolean(CLOUD_UPLOAD_SWITCH_KEY + "-" + accountSignature, isEnabled).commit();
    }

    public String getCloudUploadRepoName(String accountSignature) {
        return sharedPref.getString(SHARED_PREF_CLOUD_UPLOAD_REPO_NAME + "-" + accountSignature, null);
    }

    public String getCloudUploadRepoId(String accountSignature) {
        return sharedPref.getString(SettingsManager.SHARED_PREF_CLOUD_UPLOAD_REPO_ID + "-" + accountSignature, null);
    }

    public String getContactsUploadRepoId() {
        return sharedPref.getString(SettingsManager.SHARED_PREF_CONTACTS_UPLOAD_REPO_ID, null);
    }

    public String getCloudUploadDataPath(String accountSignature) {
        return sharedPref.getString(SHARED_PREF_CLOUD_UPLOAD_DATA_PATH + "-" + accountSignature, null);
    }

    public void saveCloudUploadRepoInfo(String accountSignature, String repoId, String repoName, String dataPath) {
        editor.putString(SHARED_PREF_CLOUD_UPLOAD_REPO_ID + "-" + accountSignature, repoId);
        editor.putString(SHARED_PREF_CLOUD_UPLOAD_REPO_NAME + "-" + accountSignature, repoName);
        editor.putString(SHARED_PREF_CLOUD_UPLOAD_DATA_PATH + "-" + accountSignature, dataPath);
        editor.commit();
    }


    public boolean checkUploadNetworkAvailable(String accountSignature) {
        if (!Utils.isNetworkOn()) {
            return false;
        }
        // user does not allow mobile connections
        if (!Utils.isWiFiOn() && !isCloudUploadDataPlanAllowed(accountSignature)) {
            return false;
        }
        // Wi-Fi or 2G/3G/4G connections available
        return true;
    }

    public boolean isCloudUploadDataPlanAllowed(String accountSignature) {
        return settingsSharedPref.getBoolean(CLOUD_UPLOAD_ALLOW_DATA_PLAN_SWITCH_KEY + "-" + accountSignature, false);
    }

    public int getCloudUploadSyncState(String accountSignature){
        return settingsSharedPref.getInt(CLOUD_UPLOAD_SYNC_TYPE_SWITCH_KEY + "-" + accountSignature, 0);
    }

    public boolean isCloudUploadSyncAllowed(String accountSignature, int syncType){
        return (getCloudUploadSyncState(accountSignature)&syncType) > 0;
    }

    public boolean isAlbumVideosUploadAllowed(String accountSignature) {
        return settingsSharedPref.getBoolean(ALBUM_UPLOAD_ALLOW_VIDEOS_SWITCH_KEY + "-" + accountSignature, false);
    }

    public void saveCloudUploadDataPlanAllowed(String accountSignature, boolean isAllowed) {
        settingsSharedPref.edit().putBoolean(CLOUD_UPLOAD_ALLOW_DATA_PLAN_SWITCH_KEY + "-" + accountSignature, isAllowed).commit();
    }

    public void saveCloudUploadSyncState(String accountSignature, int syncState) {
        settingsSharedPref.edit().putInt(CLOUD_UPLOAD_SYNC_TYPE_SWITCH_KEY + "-" + accountSignature, syncState).commit();
    }

    public void saveCloudUploadSyncAllowed(String accountSignature, int syncType, boolean isAllowed) {
        int syncState = getCloudUploadSyncState(accountSignature);
        if(isAllowed){
            syncState |= syncType;
        }else{
            syncState &= (~syncType);
        }
        saveCloudUploadSyncState(accountSignature, syncState);
    }

    public void saveAlbumVideosUploadAllowed(String accountSignature, boolean isVideosUploadAllowed) {
        settingsSharedPref.edit().putBoolean(ALBUM_UPLOAD_ALLOW_VIDEOS_SWITCH_KEY + "-" + accountSignature, isVideosUploadAllowed).commit();
    }

    public void setAlbumUploadBucketList(String accountSignature, List<String> list) {
        String s = TextUtils.join(",", list);
        sharedPref.edit().putString(SHARED_PREF_ALBUM_UPLOAD_BUCKETS + "-" + accountSignature, s).commit();
    }

    public List<String> getAlbumUploadBucketList(String accountSignature) {
        String s = sharedPref.getString(SHARED_PREF_ALBUM_UPLOAD_BUCKETS + "-" + accountSignature, "");
        return Arrays.asList(TextUtils.split(s, ","));
    }

    public void saveSortFilesPref(int type, int order) {
        editor.putInt(SORT_FILES_TYPE, type).commit();
        editor.putInt(SORT_FILES_ORDER, order).commit();
    }


    public int getSortFilesTypePref() {
        return sharedPref.getInt(SORT_FILES_TYPE, 0);
    }

    public int getSortFilesOrderPref() {
        return sharedPref.getInt(SORT_FILES_ORDER, 0);
    }

    public int getStorageDir() {
        return sharedPref.getInt(SHARED_PREF_STORAGE_DIR, Integer.MIN_VALUE);
    }

    public void setStorageDir(int dir) {
        editor.putInt(SHARED_PREF_STORAGE_DIR, dir).commit();
    }

    public void saveContactsUploadRepoInfo(String repoId, String repoName) {
        editor.putString(SHARED_PREF_CONTACTS_UPLOAD_REPO_ID, repoId);
        editor.putString(SHARED_PREF_CONTACTS_UPLOAD_REPO_NAME, repoName);
        editor.commit();
    }

    public void saveUploadCompletedTime(String completedTime) {
        editor.putString(UPLOAD_COMPLETED_TIME, completedTime);
        editor.commit();
    }

    public String getUploadCompletedTime() {
        return sharedPref.getString(SettingsManager.UPLOAD_COMPLETED_TIME, null);
    }

    public void savePrivacyPolicyConfirmed(int type) {
        editor.putInt(PRIVACY_POLICY_CONFIRMED, type).commit();
    }

    public int getPrivacyPolicyConfirmed() {
        return sharedPref.getInt(PRIVACY_POLICY_CONFIRMED, 0);
    }
}
