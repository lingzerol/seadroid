package com.seafile.seadroid2.upload;


import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.SharedPreferences;
import android.os.Build;
import android.support.annotation.RequiresApi;

import com.seafile.seadroid2.SettingsManager;
import com.seafile.seadroid2.account.Account;
import com.seafile.seadroid2.account.AccountManager;

/**
 * This service monitors the media provider content provider for new images/videos.
 */
@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class MediaSchedulerService extends JobService {
    private SettingsManager mSettingsManager;
    private AccountManager accountManager;

    @Override
    public boolean onStartJob(JobParameters jobParameters) {
        mSettingsManager = SettingsManager.instance();
        mSettingsManager.registerSharedPreferencesListener(settingsListener);
        accountManager = new AccountManager(getApplicationContext());
        for(Account account: accountManager.getSignedInAccountList()) {
//            if (UploadManager.isSyncable(account) && mSettingsManager.isAlbumVideosUploadAllowed(account.getSignature())) {
            if (UploadManager.isSyncable(account)){
                UploadManager.performFullSync(account);
            }
        }
        jobFinished(jobParameters, true);
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters jobParameters) {
        if (mSettingsManager != null) {
            mSettingsManager.unregisterSharedPreferencesListener(settingsListener);
        }
        return false;
    }


    /**
     * If camera upload settings have changed, we might have to trigger a full resync.
     * This listener takes care of that.
     */
    private SharedPreferences.OnSharedPreferenceChangeListener settingsListener =
            new SharedPreferences.OnSharedPreferenceChangeListener() {

                @Override
                public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {

                    boolean doFullResync = false;

                    // here we have to catch *all* the cases that might make a full resync to the repository
                    // necessary.
                    for (Account account : accountManager.getSignedInAccountList()) {
                        String accountSignature = account.getSignature();
                        switch (key) {

                            // if video upload has been switched on, do a full sync, to upload
                            // any older videos already recorded.
                            case SettingsManager.ALBUM_UPLOAD_ALLOW_VIDEOS_SWITCH_KEY:
                                if (mSettingsManager != null && mSettingsManager.isAlbumVideosUploadAllowed(accountSignature))
                                    doFullResync = true;
                                break;

                            // same goes for if the list of selected buckets has been changed
                            case SettingsManager.SHARED_PREF_ALBUM_UPLOAD_BUCKETS:
                                doFullResync = true;
                                break;

                            // the repo changed, also do a full resync
                            case SettingsManager.SHARED_PREF_CLOUD_UPLOAD_REPO_ID:
                                doFullResync = true;
                                break;
                        }

                        if (UploadManager.isSyncable(account) &&doFullResync){
                            // Log.i(DEBUG_TAG, "Doing a full resync of all media content.");
                            UploadManager.performFullSync(account);
                        }
                    }
                }
            };
}
