package com.seafile.seadroid2.upload;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import com.seafile.seadroid2.R;

public class ReadyToUploadFragment extends Fragment {

    private Button continueBtn;
    private UploadConfigActivity mActivity;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        mActivity = (UploadConfigActivity) getActivity();
        View rootView = mActivity.getLayoutInflater().inflate(R.layout.upload_ready_to_upload_fragment, null);

        continueBtn = (Button) rootView.findViewById(R.id.upload_click_to_finish_btn);
        continueBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mActivity.saveSettings();
                mActivity.finish();
            }
        });

        return rootView;
    }

}

