package com.seafile.seadroid2.upload.album;

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
import com.seafile.seadroid2.ui.activity.SeafilePathChooserActivity;
import com.seafile.seadroid2.ui.fragment.SettingsFragment;
import com.seafile.seadroid2.upload.UploadManager;
import com.seafile.seadroid2.util.SystemSwitchUtils;
import com.viewpagerindicator.LinePageIndicator;

import java.util.List;


/**
 * Camera upload configuration helper
 */
public class AlbumUploadConfigActivity extends BaseActivity {
    public  String DEBUG_TAG = "CameraUploadConfigActivity";

    private ViewPager mViewPager;
    private LinePageIndicator mIndicator;
    private BucketsFragment mBucketsFragment;
    private SettingsManager sm;
    private SeafRepo mSeafRepo;
    private Account mAccount;
    /** handling data from configuration helper */
    private boolean isChooseBothPages;
    /** handling data from cloud library page */
    private boolean isChooseLibPage;
    /** handling data from local directory page */
    private boolean isChooseDirPage;
    private int mCurrentPosition;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out);

        AccountManager accountManager = new AccountManager(this);
        mAccount = accountManager.getCurrentAccount();

        isChooseDirPage = true;

        setContentView(R.layout.upload_activity_layout);

        if (getSupportActionBar() != null)
            getSupportActionBar().hide();

        mViewPager = (ViewPager) findViewById(R.id.upload_pager);

        FragmentManager fm = getSupportFragmentManager();
        mViewPager.setAdapter(new CameraUploadConfigAdapter(fm));
        mViewPager.setOffscreenPageLimit(6);

        mIndicator = (LinePageIndicator) findViewById(R.id.upload_indicator);
        mIndicator.setViewPager(mViewPager);
        mIndicator.setOnPageChangeListener(pageChangeListener);

        if (isChooseLibPage || isChooseDirPage) {
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
        List<String> buckets = mBucketsFragment.getSelectionFragment().getSelectedBuckets();
        if(mBucketsFragment.isAutoScanSelected()){
            buckets.clear();
        }
        String accountSignature = getAccount().getSignature();
        Intent intent = new Intent();
        if (buckets == null) {
            setResult(RESULT_CANCELED, intent);
        }else{
            UploadManager.enableAccountUploadSync(getAccount(), UploadManager.ALBUM_SYNC);
            SettingsManager.instance().setAlbumUploadBucketList(accountSignature, buckets);
            setResult(RESULT_OK, intent);
        }
    }

    public Account getAccount(){
        return mAccount;
    }

    public boolean isChooseDirPage(){
        return isChooseDirPage;
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

    class CameraUploadConfigAdapter extends FragmentStatePagerAdapter {

        public CameraUploadConfigAdapter(FragmentManager fm) {
            super(fm);
        }

        // This method controls which fragment should be shown on a specific screen.
        @Override
        public Fragment getItem(int position) {
            switch (position) {
                case 0:
                    mBucketsFragment = new BucketsFragment();
                    return mBucketsFragment;
                default:
                    return null;
            }
        }

        @Override
        public int getCount() {
            return 1;
        }

    }
}
