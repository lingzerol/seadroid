package com.seafile.seadroid2.upload.file;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v4.view.ViewPager.OnPageChangeListener;
import android.view.View;

import com.google.common.collect.Lists;
import com.seafile.seadroid2.R;
import com.seafile.seadroid2.SettingsManager;
import com.seafile.seadroid2.account.Account;
import com.seafile.seadroid2.account.AccountManager;
import com.seafile.seadroid2.ui.activity.BaseActivity;
import com.seafile.seadroid2.upload.UploadManager;
import com.seafile.seadroid2.util.Utils;
import com.viewpagerindicator.LinePageIndicator;

import java.util.HashSet;
import java.util.List;
import java.util.Set;


/**
 * file upload configuration helper
 */
public class FileUploadConfigActivity extends BaseActivity {
    public  String DEBUG_TAG = "FileUploadConfigActivity";

    private ViewPager mViewPager;
    private LinePageIndicator mIndicator;
    private DirectoryFragment mDirectoryFragment;
    private LocalDirectorySelectionFragment mSelectionDirectoryFragment;
    private LocalDirectorySelectedFragment mSelectedDirectoryFragment;
    private Account mAccount;

    private int mCurrentPosition;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out);

        AccountManager accountManager = new AccountManager(this);
        mAccount = accountManager.getCurrentAccount();
        if(mAccount == null){
            Intent intent = new Intent();
            Utils.utilsLogInfo(true, "======FileUploadConfigActivity found null account.");
            setResult(RESULT_CANCELED, intent);
        }

        setContentView(R.layout.fuc_activity_layout);

        if (getSupportActionBar() != null)
            getSupportActionBar().hide();

        mViewPager = (ViewPager) findViewById(R.id.fuc_pager);

        FragmentManager fm = getSupportFragmentManager();
        mViewPager.setAdapter(new FileUploadConfigAdapter(fm));
        mViewPager.setOffscreenPageLimit(6);

        mIndicator = (LinePageIndicator) findViewById(R.id.fuc_indicator);
        mIndicator.setViewPager(mViewPager);
        mIndicator.setOnPageChangeListener(pageChangeListener);
        mIndicator.setVisibility(View.GONE);
    }

    /**
     * Page scroll listener.
     */
    private OnPageChangeListener pageChangeListener = new OnPageChangeListener() {

        @Override
        public void onPageScrollStateChanged(int scrollState) {}

        @Override
        public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
            mCurrentPosition = position;
        }

        @Override
        public void onPageSelected(int page){}
    };


    public void saveSettings() {
        List<String> uploadDirs = null;
        if(mAccount == null){
            Utils.utilsLogInfo(true,"======FileUploadConfigActivity: the account is null.======");
            return;
        }
        if(mSelectedDirectoryFragment != null) {
            uploadDirs = mSelectedDirectoryFragment.getDirs();
            Set<String> uploadDirsSet = new HashSet<String>();
            List<String> noRepeatUploadDirs = Lists.newArrayList();
            for (String syncDir : uploadDirs) {
                if (!uploadDirsSet.contains(syncDir)) {
                    noRepeatUploadDirs.add(syncDir);
                    uploadDirsSet.add(syncDir);
                }
            }
            uploadDirs = noRepeatUploadDirs;
        }
        Intent intent = new Intent();
        if(uploadDirs == null || uploadDirs.isEmpty()){
            UploadManager.disableAccountUploadSync(mAccount, UploadManager.FILE_SYNC);
            setResult(RESULT_CANCELED, intent);
        } else {
            String accountSignature = mAccount.getSignature();
            UploadManager.enableAccountUploadSync(mAccount, UploadManager.FILE_SYNC);
            SettingsManager.instance().setFileUploadDirs(accountSignature, uploadDirs);
            setResult(RESULT_OK, intent);
        }
    }


    private DirectoryFragment getDirectoryFragment(){
        if (mDirectoryFragment == null) {
            if (mSelectionDirectoryFragment == null) {
                mSelectionDirectoryFragment = new LocalDirectorySelectionFragment();
            }
            if (mSelectedDirectoryFragment == null) {
                mSelectedDirectoryFragment = new LocalDirectorySelectedFragment();
            }
            mDirectoryFragment = new DirectoryFragment();
            mDirectoryFragment.initFragments(mSelectedDirectoryFragment, mSelectionDirectoryFragment);
        }
        return mDirectoryFragment;
    }

    public List<String> getChosenDirectories(){
        return SettingsManager.instance().getFileUploadDirs(mAccount.getSignature());
    }

    public void addSelectedLocalDirectory(List<String> paths){
        mSelectedDirectoryFragment.addDirs(paths);
        mDirectoryFragment.switchFragment();
    }

    @Override
    public void onBackPressed() {
        if(mCurrentPosition == 0){
            if(!mDirectoryFragment.stepBack()){
                setResult(RESULT_CANCELED);
                super.onBackPressed();
            }
        }else {
            mCurrentPosition -= 1;
            mIndicator.setCurrentItem(mCurrentPosition);
        }
    }

    public Account getAccount(){
        return mAccount;
    }

    public void switchDirectoryFragment(){
        if(mCurrentPosition == 0){
            mDirectoryFragment.switchFragment();
        }
    }

    class FileUploadConfigAdapter extends FragmentStatePagerAdapter {

        public FileUploadConfigAdapter(FragmentManager fm) {
            super(fm);
        }

        // This method controls which fragment should be shown on a specific screen.
        @Override
        public Fragment getItem(int position) {
            // Assign the appropriate screen to the fragment object, based on which screen is displayed.
            switch (position) {
                case 0:
                    return getDirectoryFragment();
                default:
                    return null;
            }

        }

        @Override
        public int getCount() {
            return 1;
        }

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
    }
}
