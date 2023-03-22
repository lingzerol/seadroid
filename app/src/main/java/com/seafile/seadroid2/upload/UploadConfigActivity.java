package com.seafile.seadroid2.upload;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v4.view.ViewPager.OnPageChangeListener;
import android.view.View;

import com.seafile.seadroid2.R;
import com.seafile.seadroid2.SettingsManager;
import com.seafile.seadroid2.account.Account;
import com.seafile.seadroid2.account.AccountManager;
import com.seafile.seadroid2.data.SeafRepo;
import com.seafile.seadroid2.ui.activity.BaseActivity;
import com.seafile.seadroid2.ui.fragment.SettingsFragment;
import com.seafile.seadroid2.util.SystemSwitchUtils;
import com.viewpagerindicator.LinePageIndicator;


/**
 * Camera upload configuration helper
 */
public class UploadConfigActivity extends BaseActivity {
    public  String DEBUG_TAG = "UploadConfigActivity";

    private ViewPager mViewPager;
    private LinePageIndicator mIndicator;
    private CloudLibraryFragment mCloudLibFragment;
    private HowToUploadFragment mHowToUploadFragment;
    private Account mAccount;
    /** handling data from cloud library page */
    private boolean isChooseLibPage;

    private int mCurrentPosition;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out);

        setContentView(R.layout.upload_activity_layout);

        AccountManager accountManager = new AccountManager(this);
        mAccount = accountManager.getCurrentAccount();

        if (getSupportActionBar() != null)
            getSupportActionBar().hide();

        isChooseLibPage = getIntent().getBooleanExtra(SettingsFragment.CLOUD_UPLOAD_REMOTE_LIBRARY, false);

        mViewPager = (ViewPager) findViewById(R.id.upload_pager);

        FragmentManager fm = getSupportFragmentManager();
        mViewPager.setAdapter(new UploadConfigAdapter(fm));
        mViewPager.setOffscreenPageLimit(6);

        mIndicator = (LinePageIndicator) findViewById(R.id.upload_indicator);
        mIndicator.setViewPager(mViewPager);
        mIndicator.setOnPageChangeListener(pageChangeListener);

        if (isChooseLibPage) {
            mIndicator.setVisibility(View.GONE);
        }
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
        SystemSwitchUtils.getInstance(this).syncSwitchUtils();
        if(isChooseLibPage && mCloudLibFragment == null || !isChooseLibPage && (mCloudLibFragment == null || mHowToUploadFragment == null)){
            Intent intent = new Intent();
            setResult(RESULT_CANCELED, intent);
        }
        SeafRepo seafRepo = mCloudLibFragment.getAccountOrReposSelectionFragment().getChosenRepo();
        String dataPath = mCloudLibFragment.getAccountOrReposSelectionFragment().getChosenDir();

        Intent intent = new Intent();
        if(mAccount != null && seafRepo != null && dataPath != null){
            String accountSignature = mAccount.getSignature();
            SettingsManager.instance().saveCloudUploadRepoInfo(accountSignature, seafRepo.id, seafRepo.name, dataPath);
            if(!isChooseLibPage && mHowToUploadFragment != null) {
                SettingsManager.instance().saveCloudUploadDataPlanAllowed(accountSignature, mHowToUploadFragment.isDataAllowed());
            }
            if(!isChooseLibPage) {
                UploadManager.enableAccountUpload(mAccount);
            }
            setResult(RESULT_OK, intent);
        }else {
            if(!isChooseLibPage){
                UploadManager.disableAccountUpload(mAccount);
            }
            setResult(RESULT_CANCELED, intent);
        }
    }


    @Override
    public void onBackPressed() {
        if (mCurrentPosition == 0) {
            setResult(RESULT_CANCELED);
            super.onBackPressed();
        } else {
            // navigate to previous page when press back button
            mCurrentPosition -= 1;
            mIndicator.setCurrentItem(mCurrentPosition);
        }
    }

    public Account getAccount(){
        return mAccount;
    }
    public boolean isChooseLibPage() {
        return isChooseLibPage;
    }

    class UploadConfigAdapter extends FragmentStatePagerAdapter {

        public UploadConfigAdapter(FragmentManager fm) {
            super(fm);
        }

        // This method controls which fragment should be shown on a specific screen.
        @Override
        public Fragment getItem(int position) {

            if (isChooseLibPage) {
                if(position == 0){
                    if(mCloudLibFragment == null){
                        mCloudLibFragment = new CloudLibraryFragment();
                    }
                    return mCloudLibFragment;
                }else{
                    return null;
                }
            }


            // Assign the appropriate screen to the fragment object, based on which screen is displayed.
            switch (position) {
                case 0:
                    return new ConfigWelcomeFragment();
                case 1:
                    if(mHowToUploadFragment == null){
                        mHowToUploadFragment = new HowToUploadFragment();
                    }
                    return mHowToUploadFragment;
                case 2:
                    if(mCloudLibFragment == null){
                        mCloudLibFragment = new CloudLibraryFragment();
                    }
                    return mCloudLibFragment;
                case 3:
                    return new ReadyToUploadFragment();
                default:
                    return null;
            }
        }

        @Override
        public int getCount() {
            if (isChooseLibPage)
                return 1;
            else
                return 4;
        }

    }
}
