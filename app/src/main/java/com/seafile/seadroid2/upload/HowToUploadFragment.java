package com.seafile.seadroid2.upload;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.RadioGroup.OnCheckedChangeListener;

import com.seafile.seadroid2.R;
import com.seafile.seadroid2.SettingsManager;

/**
 * How to upload fragment
 */
public class HowToUploadFragment extends Fragment {

    private RadioButton mDataPlanRadioBtn;
    private RadioGroup mRadioGroup;
    private boolean isDataAllowed = false;

    private UploadConfigActivity mActivity;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        mActivity = (UploadConfigActivity) getActivity();
        View rootView = mActivity.getLayoutInflater().inflate(R.layout.upload_how_to_upload_fragment, null);

        mRadioGroup = (RadioGroup) rootView.findViewById(R.id.upload_wifi_radio_group);
        mDataPlanRadioBtn = (RadioButton) rootView.findViewById(R.id.upload_wifi_or_data_plan_rb);

        if (SettingsManager.instance().isCloudUploadDataPlanAllowed(mActivity.getAccount().getSignature())) {
            mDataPlanRadioBtn.setChecked(true);
        }

        mRadioGroup.setOnCheckedChangeListener(new OnCheckedChangeListener() {

            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                switch (checkedId) {
                    case R.id.upload_wifi_only_rb:
                        // WiFi only
                        isDataAllowed = false;
                        break;
                    case R.id.upload_wifi_or_data_plan_rb:
                        // WiFi and data plan
                        isDataAllowed = true;
                        break;
                }

            }

        });

        return rootView;
    }

    public boolean isDataAllowed(){
        return isDataAllowed;
    }
}

