package com.seafile.seadroid2.ui.fragment;

import android.app.Activity;
import android.app.Presentation;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;
import android.support.v7.app.AlertDialog;
import android.text.Html;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.google.common.collect.Maps;
import com.seafile.seadroid2.R;
import com.seafile.seadroid2.SeadroidApplication;
import com.seafile.seadroid2.SeafException;
import com.seafile.seadroid2.SettingsManager;
import com.seafile.seadroid2.account.Account;
import com.seafile.seadroid2.account.AccountInfo;
import com.seafile.seadroid2.account.AccountManager;
import com.seafile.seadroid2.upload.UploadConfigActivity;
import com.seafile.seadroid2.upload.UploadManager;
import com.seafile.seadroid2.upload.album.AlbumUploadConfigActivity;
import com.seafile.seadroid2.upload.album.GalleryBucketUtils;
import com.seafile.seadroid2.data.SyncEvent;
import com.seafile.seadroid2.data.DataManager;
import com.seafile.seadroid2.data.DatabaseHelper;
import com.seafile.seadroid2.data.ServerInfo;
import com.seafile.seadroid2.data.StorageManager;
import com.seafile.seadroid2.gesturelock.LockPatternUtils;
import com.seafile.seadroid2.ui.activity.BrowserActivity;
import com.seafile.seadroid2.ui.activity.CreateGesturePasswordActivity;
import com.seafile.seadroid2.ui.activity.PrivacyPolicyActivity;
import com.seafile.seadroid2.ui.activity.SettingsActivity;
import com.seafile.seadroid2.ui.dialog.ClearCacheTaskDialog;
import com.seafile.seadroid2.ui.dialog.ClearPasswordTaskDialog;
import com.seafile.seadroid2.ui.dialog.SwitchStorageTaskDialog;
import com.seafile.seadroid2.ui.dialog.TaskDialog.TaskDialogListener;
import com.seafile.seadroid2.upload.file.FileUploadConfigActivity;
import com.seafile.seadroid2.util.ConcurrentAsyncTask;
import com.seafile.seadroid2.util.Utils;

import org.apache.commons.io.FileUtils;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

public class SettingsFragment extends CustomPreferenceFragment {
    private static final String DEBUG_TAG = "SettingsFragment";

//    public static final String CAMERA_UPLOAD_BOTH_PAGES = "com.seafile.seadroid2.camera.upload";
//    public static final String CAMERA_UPLOAD_REMOTE_LIBRARY = "com.seafile.seadroid2.camera.upload.library";
//    public static final String CAMERA_UPLOAD_LOCAL_DIRECTORIES = "com.seafile.seadroid2.camera.upload.directories";
//    public static final String CONTACTS_UPLOAD_REMOTE_LIBRARY = "com.seafile.seadroid2.contacts.upload.library";
    public static final int CHOOSE_CLOUD_UPLOAD_REQUEST = 2;
    public static final int CHOOSE_ALBUM_UPLOAD_REQUEST = 3;
    public static final int CHOOSE_FILE_UPLOAD_REQUEST = 4;

    public static final String FILE_UPLOAD_ONLY_UPLOAD = "com.seafile.seadroid2.upload.file.only_upload";
    public static final String FILE_UPLOAD_ONLY_SYNC = "com.seafile.seadroid2.upload.file.only_sync";

    public static final String CLOUD_UPLOAD_BOTH_PAGES = "com.seafile.seadroid2.upload";
    public static final String CLOUD_UPLOAD_REMOTE_LIBRARY = "com.seafile.seadroid2.upload.remote_library";
    public static final String ALBUM_UPLOAD_BOTH_PAGES = "com.seafile.seadroid2.upload.album";
    public static final String ALBUM_UPLOAD_LOCAL_DIRECTORIES = "com.seafile.seadroid2.upload.album.directories";

    //    public static final int CHOOSE_CONTACTS_UPLOAD_REQUEST = 3;
    // Account Info
    private static Map<String, AccountInfo> accountInfoMap = Maps.newHashMap();

    // Cloud upload
    private CheckBoxPreference cUploadSwitch;
    private PreferenceCategory cUploadCategory;
    private PreferenceScreen cUploadAdvancedScreen;
    private PreferenceCategory cUploadAdvancedCategory;
    private Preference cUploadRepoPref;
    private CheckBoxPreference cbDataPlan;

    // Album upload
    private CheckBoxPreference aUploadSwitch;
    private PreferenceCategory aUploadAdvancedCategory;
    private CheckBoxPreference aCustomDirectoriesPref;
    private Preference aLocalDirectoriesPref;
    private CheckBoxPreference aVideoAllowed;

    // CallLog upload
    private CheckBoxPreference callUploadSwitch;

    // file upload
    private CheckBoxPreference fileUploadSwitch;
    private Preference fileLocalDirectoriesPref;
    private PreferenceCategory fileAdvancedCategory;

    // sms upload
    private CheckBoxPreference smsUploadSwitch;

    // privacy
    private PreferenceCategory cPrivacyCategory;
    private Preference clientEncPref;

    private SettingsActivity mActivity;
    private String appVersion;
//    public ContactsUploadManager contactsManager;
    private AccountManager accountMgr;
    private DataManager dataMgr;
    private StorageManager storageManager = StorageManager.getInstance();
//    private PreferenceCategory cContactsCategory;
//    private Preference cContactsRepoPref;
//    private Preference cContactsRepoTime;
//    private Preference cContactsRepoBackUp;
//    private Preference cContactsRepoRecovery;
    private long mMtime;
    private Preference cUploadRepoState;

    @Override
    public void onAttach(Activity activity) {
        Log.d(DEBUG_TAG, "onAttach");
        super.onAttach(activity);

        // global variables
        mActivity = (SettingsActivity) getActivity();
        accountMgr = new AccountManager(mActivity);
//        contactsManager = new ContactsUploadManager(mActivity.getApplicationContext());
        Account act = accountMgr.getCurrentAccount();
        dataMgr = new DataManager(act);
    }

    public void onCreate(Bundle savedInstanceState) {
        Log.d(DEBUG_TAG, "onCreate");
        super.onCreate(savedInstanceState);
        EventBus.getDefault().register(this);
        SettingsManager.instance().registerSharedPreferencesListener(settingsListener);
        Account account = accountMgr.getCurrentAccount();
        if (!Utils.isNetworkOn()) {
            mActivity.showShortToast(mActivity, R.string.network_down);
            return;
        }

        ConcurrentAsyncTask.execute(new RequestAccountInfoTask(), account);

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        EventBus.getDefault().unregister(this);
        Log.d(DEBUG_TAG, "onDestroy()");
        SettingsManager.instance().unregisterSharedPreferencesListener(settingsListener);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        Log.d(DEBUG_TAG, "onViewCreated");
        super.onViewCreated(view, savedInstanceState);

        addPreferencesFromResource(R.xml.settings);

        // User info
        String identifier = getCurrentUserIdentifier();
        findPreference(SettingsManager.SETTINGS_ACCOUNT_INFO_KEY).setSummary(identifier);

        // Space used
        Account currentAccount = accountMgr.getCurrentAccount();
        if (currentAccount != null) {
            String signature = currentAccount.getSignature();
            AccountInfo info = getAccountInfoBySignature(signature);
            if (info != null) {
                String spaceUsed = info.getSpaceUsed();
                findPreference(SettingsManager.SETTINGS_ACCOUNT_SPACE_KEY).setSummary(spaceUsed);
            }
        }

        // Gesture Lock
        findPreference(SettingsManager.GESTURE_LOCK_SWITCH_KEY).setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                if (newValue instanceof Boolean) {
                    boolean isChecked = (Boolean) newValue;
                    if (isChecked) {
                        // inverse checked status
                        Intent newIntent = new Intent(getActivity(), CreateGesturePasswordActivity.class);
                        newIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        startActivityForResult(newIntent, SettingsManager.GESTURE_LOCK_REQUEST);
                    } else {
                        LockPatternUtils mLockPatternUtils = new LockPatternUtils(getActivity());
                        mLockPatternUtils.clearLock();
                    }
                    return true;
                }

                return false;
            }
        });

        // Sign out
        findPreference(SettingsManager.SETTINGS_ACCOUNT_SIGN_OUT_KEY).setOnPreferenceClickListener(new OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {

                // popup a dialog to confirm sign out request
                final AlertDialog.Builder builder = new AlertDialog.Builder(mActivity);
                builder.setTitle(getString(R.string.settings_account_sign_out_title));
                builder.setMessage(getString(R.string.settings_account_sign_out_confirm));
                builder.setPositiveButton(getString(R.string.confirm), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Account account = accountMgr.getCurrentAccount();

                        // sign out operations
                        accountMgr.signOutAccount(account);

                        // password auto clear
                        if (SettingsManager.instance().isPasswordAutoClearEnabled()) {
                            clearPasswordSilently();
                        }

                        // restart BrowserActivity (will go to AccountsActivity)
                        Intent intent = new Intent(mActivity, BrowserActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        mActivity.startActivity(intent);
                        mActivity.finish();
                    }
                });
                builder.setNegativeButton(getString(R.string.cancel), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // dismiss
                        dialog.dismiss();
                    }
                });
                builder.show();
                return true;
            }
        });

        findPreference(SettingsManager.CLEAR_PASSOWR_SWITCH_KEY).setOnPreferenceClickListener(new OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                // clear password
                clearPassword();
                return true;
            }
        });

        // auto clear passwords when logout
        findPreference(SettingsManager.AUTO_CLEAR_PASSOWR_SWITCH_KEY).setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                if (newValue instanceof Boolean) {
                    boolean isChecked = (Boolean) newValue;
                    // inverse checked status
                    SettingsManager.instance().setupPasswordAutoClear(!isChecked);
                    return true;
                }

                return false;
            }
        });
        if (currentAccount != null) {
            final ServerInfo serverInfo = accountMgr.getServerInfo(currentAccount);

            cPrivacyCategory = (PreferenceCategory) findPreference(SettingsManager.PRIVACY_CATEGORY_KEY);
            // Client side encryption for encrypted Library
            clientEncPref = findPreference(SettingsManager.CLIENT_ENC_SWITCH_KEY);
            clientEncPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    if (newValue instanceof Boolean) {
                        boolean isChecked = (Boolean) newValue;
                        // inverse checked status
                        SettingsManager.instance().setupEncrypt(!isChecked);
                        return true;
                    }

                    return false;
                }
            });

            if (serverInfo != null && !serverInfo.canLocalDecrypt()) {
                cPrivacyCategory.removePreference(clientEncPref);
            }
        }

        initCloudUploadSettings();

//        refreshContactsView();

        // App Version
        try {
            appVersion = mActivity.getPackageManager().getPackageInfo(mActivity.getPackageName(), 0).versionName;
        } catch (NameNotFoundException e) {
            Log.e(DEBUG_TAG, "app version name not found exception");
            appVersion = getString(R.string.not_available);
        }
        findPreference(SettingsManager.SETTINGS_ABOUT_VERSION_KEY).setSummary(appVersion);

        // About author
        findPreference(SettingsManager.SETTINGS_ABOUT_AUTHOR_KEY).setOnPreferenceClickListener(new OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {

                AlertDialog.Builder builder = new AlertDialog.Builder(mActivity);
                // builder.setIcon(R.drawable.icon);
                builder.setMessage(Html.fromHtml(getString(R.string.settings_about_author_info, appVersion)));
                builder.show();
                return true;
            }
        });

        findPreference(SettingsManager.SETTINGS_PRIVACY_POLICY_KEY).setOnPreferenceClickListener(new OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {

                Intent intent = new Intent(mActivity, PrivacyPolicyActivity.class);
                mActivity.startActivity(intent);
                return true;
            }
        });

        // Cache size
        calculateCacheSize();

        // Clear cache
        findPreference(SettingsManager.SETTINGS_CLEAR_CACHE_KEY).setOnPreferenceClickListener(new OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                clearCache();
                return true;
            }
        });

        // Storage selection only works on KitKat or later
        if (storageManager.supportsMultipleStorageLocations()) {
            updateStorageLocationSummary();
            findPreference(SettingsManager.SETTINGS_CACHE_DIR_KEY).setOnPreferenceClickListener(new OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    new SwitchStorageTaskDialog().show(getFragmentManager(), "Select cache location");
                    return true;
                }
            });
        } else {
            PreferenceCategory cCacheCategory = (PreferenceCategory)findPreference(SettingsManager.SETTINGS_CACHE_CATEGORY_KEY);
            cCacheCategory.removePreference(findPreference(SettingsManager.SETTINGS_CACHE_DIR_KEY));
        }

    }

    //contacts  backup
//    private void backupContacts() {
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
//            if (mActivity.checkSelfPermission(Manifest.permission.READ_CONTACTS) !=
//                    PackageManager.PERMISSION_GRANTED) {
//                //if not have read contacts permission to  request
//                mActivity.requestReadContactsPermission();
//            } else {
//                // have read contacts permission  to  show backup dialog
//                showUploadContactsDialog();
//            }
//        } else {
//            showUploadContactsDialog();
//        }
//    }

//    private void recoveryContacts() {
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
//            if (mActivity.checkSelfPermission(Manifest.permission.READ_CONTACTS) !=
//                    PackageManager.PERMISSION_GRANTED) {
//                //if not have read contacts permission to  request
//                mActivity.requestReadContactsPermission();
//            } else {
//                // have read contacts permission  to  show recovery dialog
//                showRecoveryContactsDialog();
//            }
//        } else {
//            showRecoveryContactsDialog();
//        }
//    }
//
//    private void showRecoveryContactsDialog() {
//        ContactsDialog contactsDialog = new ContactsDialog(getActivity(), ContactsDialog.CONTACTS_RECOVERY);
//        contactsDialog.show(getFragmentManager(), "SettingsFragment");
//    }
//

//    public void showUploadContactsDialog() {
//
//        final ContactsDialog contactsDialog = new ContactsDialog(mActivity, ContactsDialog.CONTACTS_BACKUP);
//        contactsDialog.setTaskDialogLisenter(new TaskDialogListener() {
//            @Override
//            public void onTaskSuccess() {
//                long timeMillis = System.currentTimeMillis();
//                String s = Utils.translateCommitTime(timeMillis * 1000);
//                cContactsRepoTime.setSummary(s);
//            }
//        });
//        contactsDialog.show(mActivity.getSupportFragmentManager(), "SettingsFragment");
//    }


//    private void refreshContactsView() {
//        ((CheckBoxPreference) findPreference(SettingsManager.CONTACTS_UPLOAD_SWITCH_KEY))
//                .setChecked(contactsManager.isContactsUploadEnabled());
//
//        if (!contactsManager.isContactsUploadEnabled()) {
//            cContactsCategory.removePreference(cContactsRepoPref);
//            cContactsCategory.removePreference(cContactsRepoTime);
//            cContactsCategory.removePreference(cContactsRepoBackUp);
//            cContactsCategory.removePreference(cContactsRepoRecovery);
//        } else {
//            cContactsCategory.addPreference(cContactsRepoPref);
//            cContactsCategory.addPreference(cContactsRepoTime);
//            cContactsCategory.addPreference(cContactsRepoBackUp);
//            cContactsCategory.addPreference(cContactsRepoRecovery);
//
//            Account camAccount = contactsManager.getContactsAccount();
//            if (camAccount != null && SettingsManager.instance().getContactsUploadRepoName() != null) {
//                cContactsRepoPref.setSummary(camAccount.getSignature()
//                        + "/" + SettingsManager.instance().getContactsUploadRepoName()
//                        + "/" + SettingsActivity.BASE_DIR);
//            }
//
//            //show  backup  time
//            DataManager dataManager = new DataManager(camAccount);
//            String repoId = SettingsManager.instance().getContactsUploadRepoId();
//            if (repoId != null) {
//                List<SeafDirent> dirents = dataManager.getCachedDirents(repoId, "/");
//                if (dirents != null) {
//                    for (int i = 0; i < dirents.size(); i++) {
//                        SeafDirent seafDirent = dirents.get(i);
//                        if (seafDirent.isDir() && seafDirent.getTitle().equals(SettingsActivity.BASE_DIR)) {
//                            String path = Utils.pathJoin("/", seafDirent.name);
//                            List<SeafDirent> childDirents = dataManager.getCachedDirents(repoId, path);
//                            if (childDirents != null) {
//                                for (int j = 0; j < childDirents.size(); j++) {
//                                    SeafDirent childFile = childDirents.get(j);
//                                    if (!childFile.isDir()) {
//                                        String title = childFile.getTitle();
//                                        if (title.indexOf("contacts") != -1) {
//                                            if (seafDirent.mtime > mMtime) {
//                                                mMtime = seafDirent.mtime;
//                                            }
//                                        }
//                                    }
//                                }
//                            }
//                        }
//                    }
//                    if (mMtime > 0) {
//                        cContactsRepoTime.setSummary(Utils.translateCommitTime(mMtime * 1000));
//                    }
//                }
//            }
//        }
//    }


    private void initCloudUploadSettings(){
        // cloud Upload
        cUploadCategory = (PreferenceCategory) findPreference(SettingsManager.CLOUD_UPLOAD_CATEGORY_KEY);
        cUploadAdvancedScreen = (PreferenceScreen) findPreference(SettingsManager.CLOUD_UPLOAD_ADVANCED_SCREEN_KEY);
        cUploadAdvancedCategory = (PreferenceCategory) findPreference(SettingsManager.CLOUD_UPLOAD_ADVANCED_CATEGORY_KEY);

        cUploadSwitch = (CheckBoxPreference) findPreference(SettingsManager.CLOUD_UPLOAD_SWITCH_KEY);
        cUploadSwitch.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                if (newValue instanceof Boolean) {
                    boolean isChecked = (Boolean) newValue;
                    Account account = accountMgr.getCurrentAccount();
                    if (!isChecked) {
                        cUploadCategory.removePreference(cUploadRepoPref);
                        cUploadCategory.removePreference(cUploadAdvancedScreen);
                        UploadManager.disableAccountUpload(account);
                    }
                    else {
                        Intent intent = new Intent(mActivity, UploadConfigActivity.class);
                        startActivityForResult(intent, CHOOSE_CLOUD_UPLOAD_REQUEST);
                    }
                    return true;
                }

                return false;
            }
        });

        // Change upload library
        cUploadRepoPref = findPreference(SettingsManager.CLOUD_UPLOAD_REPO_KEY);
        cUploadRepoPref.setOnPreferenceClickListener(new OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {

                // choose remote library
                Intent intent = new Intent(mActivity, UploadConfigActivity.class);
                intent.putExtra(CLOUD_UPLOAD_REMOTE_LIBRARY, true);
                startActivityForResult(intent, CHOOSE_CLOUD_UPLOAD_REQUEST);

                return true;
            }
        });

        cUploadRepoState = findPreference(SettingsManager.CLOUD_UPLOAD_STATE);
        cUploadRepoState.setSummary(Utils.getUploadStateShow(getActivity()));
        cUploadRepoState.setOnPreferenceClickListener(new OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Account account = accountMgr.getCurrentAccount();
                if(account != null && UploadManager.isSyncable(account)) {
                    Bundle settingsBundle = new Bundle();
                    settingsBundle.putBoolean(
                            ContentResolver.SYNC_EXTRAS_MANUAL, true);
                    settingsBundle.putBoolean(
                            ContentResolver.SYNC_EXTRAS_EXPEDITED, true);
                    ContentResolver.requestSync(account.getAndroidAccount(), UploadManager.AUTHORITY, settingsBundle);
                }
                return true;
            }
        });

        cbDataPlan = ((CheckBoxPreference) findPreference(SettingsManager.CLOUD_UPLOAD_ALLOW_DATA_PLAN_SWITCH_KEY));
        cbDataPlan.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                if (newValue instanceof Boolean) {
                    boolean isChecked = (Boolean) newValue;
                    SettingsManager.instance().saveCloudUploadDataPlanAllowed(accountMgr.getCurrentAccount().getSignature(), isChecked);
                    return true;
                }
                return false;
            }
        });

        initAlbumUploadSettings();
        initCallLogUploadSettings();
        initFileUploadSettings();
        initSmsUploadSettings();

        refreshCloudUploadView();
    }

    private void initAlbumUploadSettings(){
        // change local folder CheckBoxPreference
        aUploadAdvancedCategory = (PreferenceCategory) findPreference(SettingsManager.ALBUM_UPLOAD_ADVANCED_CATEGORY_KEY);
        aVideoAllowed = ((CheckBoxPreference)findPreference(SettingsManager.ALBUM_UPLOAD_ALLOW_VIDEOS_SWITCH_KEY));
        aVideoAllowed.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                if (newValue instanceof Boolean) {
                    boolean isCustom = (Boolean) newValue;
                    SettingsManager.instance().saveAlbumVideosUploadAllowed(accountMgr.getCurrentAccount().getSignature(), isCustom);
                    return true;
                }
                return false;
            }
        });

        aCustomDirectoriesPref = (CheckBoxPreference) findPreference(SettingsManager.ALBUM_UPLOAD_CUSTOM_BUCKETS_KEY);
        aCustomDirectoriesPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                if (newValue instanceof Boolean) {
                    boolean isCustom = (Boolean) newValue;
                    if (!isCustom) {
                        aUploadAdvancedCategory.removePreference(aLocalDirectoriesPref);
                        scanAlbumCustomDirs(accountMgr.getCurrentAccount(), false);
                    } else {
                        aUploadAdvancedCategory.addPreference(aLocalDirectoriesPref);
                        scanAlbumCustomDirs(accountMgr.getCurrentAccount(), true);
                    }
                    return true;
                }

                return false;
            }
        });

        // change local folder Preference
        aLocalDirectoriesPref = findPreference(SettingsManager.ALBUM_UPLOAD_BUCKETS_KEY);
        aLocalDirectoriesPref.setOnPreferenceClickListener(new OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {

                // choose media buckets
                scanAlbumCustomDirs(accountMgr.getCurrentAccount(), true);

                return true;
            }
        });

        aUploadSwitch = (CheckBoxPreference) findPreference(SettingsManager.ALBUM_UPLOAD_SWITCH_KEY);
        aUploadSwitch.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                if (newValue instanceof Boolean) {
                    boolean isChecked = (Boolean) newValue;
                    if (!isChecked) {
                        aUploadAdvancedCategory.removePreference(aCustomDirectoriesPref);
                        aCustomDirectoriesPref.setChecked(false);
                        aUploadAdvancedCategory.removePreference(aLocalDirectoriesPref);
                        aUploadAdvancedCategory.removePreference(aVideoAllowed);
                        UploadManager.disableAccountUploadSync(accountMgr.getCurrentAccount(), UploadManager.ALBUM_SYNC);
                    }
                    else {
                        Intent intent = new Intent(mActivity, AlbumUploadConfigActivity.class);
                        startActivityForResult(intent, CHOOSE_ALBUM_UPLOAD_REQUEST);
                    }
                    return true;
                }

                return false;
            }
        });
    }

    private void initCallLogUploadSettings(){
        callUploadSwitch = (CheckBoxPreference) findPreference(SettingsManager.CALLLOG_UPLOAD_SWITCH_KEY);
        callUploadSwitch.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                if (newValue instanceof Boolean) {
                    boolean isChecked = (Boolean) newValue;
                    if (!isChecked) {
                        UploadManager.disableAccountUploadSync(accountMgr.getCurrentAccount(), UploadManager.CALLLOG_SYNC);
                    }
                    else {
                        UploadManager.enableAccountUploadSync(accountMgr.getCurrentAccount(), UploadManager.CALLLOG_SYNC);
                    }
                    return true;
                }
                return false;
            }
        });
    }

    private void initFileUploadSettings(){
        fileAdvancedCategory = (PreferenceCategory) findPreference(SettingsManager.FILE_UPLOAD_ADVANCED_CATEGORY_KEY);

        fileLocalDirectoriesPref = findPreference(SettingsManager.FILE_UPLOAD_DIRECTORIES_KEY);
        fileLocalDirectoriesPref.setOnPreferenceClickListener(new OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Intent intent = new Intent(mActivity, FileUploadConfigActivity.class);
                startActivityForResult(intent, CHOOSE_FILE_UPLOAD_REQUEST);
                return true;
            }
        });

        fileUploadSwitch = (CheckBoxPreference) findPreference(SettingsManager.FILE_UPLOAD_SWITCH_KEY);
        fileUploadSwitch.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                if (newValue instanceof Boolean) {
                    boolean isChecked = (Boolean) newValue;
                    if (!isChecked) {
                        fileAdvancedCategory.removePreference(fileLocalDirectoriesPref);
                        UploadManager.disableAccountUploadSync(accountMgr.getCurrentAccount(), UploadManager.FILE_SYNC);
                    }
                    else {
                        Intent intent = new Intent(mActivity, FileUploadConfigActivity.class);
                        startActivityForResult(intent, CHOOSE_FILE_UPLOAD_REQUEST);
                    }
                    return true;
                }
                return false;
            }
        });
    }

    private void initSmsUploadSettings(){
        smsUploadSwitch = (CheckBoxPreference) findPreference(SettingsManager.SMS_UPLOAD_SWITCH_KEY);
        smsUploadSwitch.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                if (newValue instanceof Boolean) {
                    boolean isChecked = (Boolean) newValue;
                    if (!isChecked) {
                        UploadManager.disableAccountUploadSync(accountMgr.getCurrentAccount(), UploadManager.SMS_SYNC);
                    }
                    else {
                        UploadManager.enableAccountUploadSync(accountMgr.getCurrentAccount(), UploadManager.SMS_SYNC);
                    }
                    return true;
                }
                return false;
            }
        });
    }

    private void clearPasswordSilently() {
        ConcurrentAsyncTask.submit(new Runnable() {
            @Override
            public void run() {
                DataManager.clearPassword();

                // clear cached data from database
                DatabaseHelper dbHelper = DatabaseHelper.getDatabaseHelper();
                dbHelper.clearEnckeys();
            }
        });
    }

    private void clearPassword() {
        ClearPasswordTaskDialog dialog = new ClearPasswordTaskDialog();
        dialog.setTaskDialogLisenter(new TaskDialogListener() {
            @Override
            public void onTaskSuccess() {
                mActivity.showShortToast(mActivity, R.string.clear_password_successful);
            }

            @Override
            public void onTaskFailed(SeafException e) {
                mActivity.showShortToast(mActivity, R.string.clear_password_failed);
            }
        });
        dialog.show(getFragmentManager(), "DialogFragment");
    }

    private void updateStorageLocationSummary() {
        String summary = storageManager.getStorageLocation().description;
        findPreference(SettingsManager.SETTINGS_CACHE_DIR_KEY).setSummary(summary);
    }

    private void refreshCloudUploadView() {
        Account camAccount = accountMgr.getCurrentAccount();
        if(camAccount == null || !UploadManager.isEnableCloudUpload(camAccount)) {
            cUploadCategory.removePreference(cUploadRepoPref);
            cUploadCategory.removePreference(cUploadAdvancedScreen);
            cUploadSwitch.setChecked(false);
            return;
        }

        String accountSignature = camAccount.getSignature();

        cUploadRepoPref.setSummary(accountSignature
                + "/" + SettingsManager.instance().getCloudUploadRepoName(accountSignature) + ":" + SettingsManager.instance().getCloudUploadDataPath(accountSignature));

        ((CheckBoxPreference) findPreference(SettingsManager.CLOUD_UPLOAD_SWITCH_KEY)).setChecked(true);

        cUploadCategory.addPreference(cUploadRepoPref);
        cUploadCategory.addPreference(cUploadAdvancedScreen);

        // data plan:
        if (cbDataPlan != null) {
            cbDataPlan.setChecked(SettingsManager.instance().isCloudUploadDataPlanAllowed(accountSignature));
        }

        refreshAlbumUploadView();
        refreshCallLogUploadView();
        refreshFileUploadView();
        refreshSmsUploadView();
    }

    private void refreshAlbumUploadView(){
        Account camAccount = accountMgr.getCurrentAccount();
        if(camAccount == null || !UploadManager.isEnableCloudUploadSync(camAccount, UploadManager.ALBUM_SYNC)){
            aUploadSwitch.setChecked(false);
            aUploadAdvancedCategory.removePreference(aLocalDirectoriesPref);
            aUploadAdvancedCategory.removePreference(aCustomDirectoriesPref);
            aUploadAdvancedCategory.removePreference(aVideoAllowed);
            return;
        }
        String accountSignature = camAccount.getSignature();

        if (aVideoAllowed != null) {
            aVideoAllowed.setChecked(SettingsManager.instance().isAlbumVideosUploadAllowed(accountSignature));
        }
        List<String> bucketNames = new ArrayList<>();
        List<String> bucketIds = SettingsManager.instance().getAlbumUploadBucketList(accountSignature);
        List<GalleryBucketUtils.Bucket> tempBuckets = GalleryBucketUtils.getMediaBuckets(getActivity().getApplicationContext());
        LinkedHashSet<GalleryBucketUtils.Bucket> bucketsSet = new LinkedHashSet<>(tempBuckets.size());
        bucketsSet.addAll(tempBuckets);
        List<GalleryBucketUtils.Bucket> allBuckets = new ArrayList<>(bucketsSet.size());
        Iterator iterator = bucketsSet.iterator();
        while (iterator.hasNext()) {
            GalleryBucketUtils.Bucket bucket = (GalleryBucketUtils.Bucket) iterator.next();
            allBuckets.add(bucket);
        }

        for (GalleryBucketUtils.Bucket bucket : allBuckets) {
            if (bucketIds.contains(bucket.id)) {
                bucketNames.add(bucket.name);
            }
        }

        aUploadAdvancedCategory.addPreference(aCustomDirectoriesPref);
        aUploadAdvancedCategory.addPreference(aVideoAllowed);
        if (bucketNames.isEmpty()) {
            aUploadAdvancedCategory.removePreference(aLocalDirectoriesPref);
            aCustomDirectoriesPref.setChecked(false);
        } else {
            aCustomDirectoriesPref.setChecked(true);
            aLocalDirectoriesPref.setSummary(TextUtils.join(", ", bucketNames));
            aUploadAdvancedCategory.addPreference(aLocalDirectoriesPref);
        }
    }

    private void refreshCallLogUploadView() {
        Account camAccount = accountMgr.getCurrentAccount();
        if(camAccount == null || !UploadManager.isEnableCloudUploadSync(camAccount, UploadManager.CALLLOG_SYNC)){
            callUploadSwitch.setChecked(false);
            return;
        }
        callUploadSwitch.setChecked(true);
    }

    private void refreshFileUploadView() {
        Account camAccount = accountMgr.getCurrentAccount();
        if(camAccount == null || !UploadManager.isEnableCloudUploadSync(camAccount, UploadManager.FILE_SYNC)){
            fileUploadSwitch.setChecked(false);
            fileAdvancedCategory.removePreference(fileLocalDirectoriesPref);
            return;
        }
        fileUploadSwitch.setChecked(true);
        fileAdvancedCategory.addPreference(fileLocalDirectoriesPref);
        fileLocalDirectoriesPref.setSummary(TextUtils.join(",", SettingsManager.instance().getFileUploadDirs(accountMgr.getCurrentAccount().getSignature())));
    }

    private void refreshSmsUploadView() {
        Account camAccount = accountMgr.getCurrentAccount();
        if(camAccount == null || !UploadManager.isEnableCloudUploadSync(camAccount, UploadManager.SMS_SYNC)){
            smsUploadSwitch.setChecked(false);
            return;
        }
        smsUploadSwitch.setChecked(true);
    }


    private void clearCache() {
        ClearCacheTaskDialog dialog = new ClearCacheTaskDialog();
        dialog.setTaskDialogLisenter(new TaskDialogListener() {
            @Override
            public void onTaskSuccess() {
                // refresh cache size
                calculateCacheSize();
                //clear Glide cache
                Glide.get(SeadroidApplication.getAppContext()).clearMemory();
                Toast.makeText(mActivity, getString(R.string.settings_clear_cache_success), Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onTaskFailed(SeafException e) {
                Toast.makeText(mActivity, getString(R.string.settings_clear_cache_failed), Toast.LENGTH_SHORT).show();
            }
        });
        dialog.show(getFragmentManager(), "DialogFragment");
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case SettingsManager.GESTURE_LOCK_REQUEST:
                if (resultCode == Activity.RESULT_OK) {


                } else if (resultCode == Activity.RESULT_CANCELED) {
                    ((CheckBoxPreference) findPreference(SettingsManager.GESTURE_LOCK_SWITCH_KEY)).setChecked(false);
                }
                break;

            case CHOOSE_CLOUD_UPLOAD_REQUEST:
                refreshCloudUploadView();
                break;
            case CHOOSE_ALBUM_UPLOAD_REQUEST:
                refreshAlbumUploadView();
                break;
            case CHOOSE_FILE_UPLOAD_REQUEST:
                refreshFileUploadView();
//            case CHOOSE_CONTACTS_UPLOAD_REQUEST:
//                if (resultCode == Activity.RESULT_OK) {
//                    if (data == null) {
//                        return;
//                    }
//                    final String repoName = data.getStringExtra(SeafilePathChooserActivity.DATA_REPO_NAME);
//                    final String repoId = data.getStringExtra(SeafilePathChooserActivity.DATA_REPO_ID);
//                    final Account account = data.getParcelableExtra(SeafilePathChooserActivity.DATA_ACCOUNT);
//                    if (repoName != null && repoId != null) {
//                        //                        Log.d(DEBUG_TAG, "Activating contacts upload to " + account + "; " + repoName);
//                        contactsManager.setContactsAccount(account);
//                        SettingsManager.instance().saveContactsUploadRepoInfo(repoId, repoName);
//                    }
//                } else if (resultCode == Activity.RESULT_CANCELED) {
//                }
////                refreshContactsView();
//                break;

            default:
                break;
        }

    }


    private void scanAlbumCustomDirs(Account account, boolean isCustomScanOn) {
        if (isCustomScanOn) {
            Intent intent = new Intent(mActivity, AlbumUploadConfigActivity.class);
            startActivityForResult(intent, CHOOSE_ALBUM_UPLOAD_REQUEST);
        } else {
            List<String> selectedBuckets = new ArrayList<>();
            SettingsManager.instance().setAlbumUploadBucketList(account.getSignature(), selectedBuckets);
            refreshAlbumUploadView();
        }
    }

    /**
     * automatically update Account info, like space usage, total space size, from background.
     */
    class RequestAccountInfoTask extends AsyncTask<Account, Void, AccountInfo> {

        @Override
        protected void onPreExecute() {
            mActivity.setSupportProgressBarIndeterminateVisibility(true);
        }

        @Override
        protected AccountInfo doInBackground(Account... params) {
            AccountInfo accountInfo = null;

            if (params == null) return null;

            try {
                // get account info from server
                accountInfo = dataMgr.getAccountInfo();
            } catch (Exception e) {
                Log.e(DEBUG_TAG, "could not get account info!", e);
            }

            return accountInfo;
        }

        @Override
        protected void onPostExecute(AccountInfo accountInfo) {
            mActivity.setSupportProgressBarIndeterminateVisibility(false);

            if (accountInfo == null) return;

            // update Account info settings
            findPreference(SettingsManager.SETTINGS_ACCOUNT_INFO_KEY).setSummary(getCurrentUserIdentifier());
            String spaceUsage = accountInfo.getSpaceUsed();
            findPreference(SettingsManager.SETTINGS_ACCOUNT_SPACE_KEY).setSummary(spaceUsage);
            Account currentAccount = accountMgr.getCurrentAccount();
            if (currentAccount != null)
                saveAccountInfo(currentAccount.getSignature(), accountInfo);
        }
    }

    public String getCurrentUserIdentifier() {
        Account account = accountMgr.getCurrentAccount();

        if (account == null)
            return "";

        return account.getDisplayName();
    }

    public void saveAccountInfo(String signature, AccountInfo accountInfo) {
        accountInfoMap.put(signature, accountInfo);
    }

    public AccountInfo getAccountInfoBySignature(String signature) {
        if (accountInfoMap.containsKey(signature))
            return accountInfoMap.get(signature);
        else
            return null;
    }

    private void calculateCacheSize() {
        ConcurrentAsyncTask.execute(new CalculateCacheTask());
    }

    class CalculateCacheTask extends AsyncTask<String, Void, Long> {

        @Override
        protected Long doInBackground(String... params) {
            return storageManager.getUsedSpace();
        }

        @Override
        protected void onPostExecute(Long aLong) {
            String total = FileUtils.byteCountToDisplaySize(aLong);
            findPreference(SettingsManager.SETTINGS_CACHE_SIZE_KEY).setSummary(total);
        }

    }

    class UpdateStorageSLocationSummaryTask extends AsyncTask<Void, Void, Void> {

        @Override
        protected Void doInBackground(Void... params) {
            return null;
        }

        @Override
        protected void onPostExecute(Void ret) {
            updateStorageLocationSummary();
        }

    }

    private SharedPreferences.OnSharedPreferenceChangeListener settingsListener =
            new SharedPreferences.OnSharedPreferenceChangeListener() {

                @Override
                public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {

                    switch (key) {
                        case SettingsManager.SHARED_PREF_STORAGE_DIR:
                            ConcurrentAsyncTask.execute(new UpdateStorageSLocationSummaryTask());
                            break;
                    }
                }
            };


    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(SyncEvent result) {

        cUploadRepoState.setSummary(Utils.getUploadStateShow(getActivity()));

        Log.d(DEBUG_TAG, "==========" + result.getLogInfo());
        Utils.utilsLogInfo(true,"==========" + result.getLogInfo());
    }

}
