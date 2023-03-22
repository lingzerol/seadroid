package com.seafile.seadroid2.upload;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import com.seafile.seadroid2.R;

/**
 * Cloud Library fragment
 */
public class CloudLibraryFragment extends Fragment {

    private UploadConfigActivity mActivity;
    private FragmentManager fm;
    private Button mDoneBtn;
    private CloudLibrarySelectionFragment mSelectionFragment;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        mActivity = (UploadConfigActivity) getActivity();
        View rootView = mActivity.getLayoutInflater().inflate(R.layout.upload_remote_library_fragment, null);

        fm = getChildFragmentManager();
        fm.beginTransaction()
                .add(R.id.upload_remote_library_list_container, getAccountOrReposSelectionFragment())
                .commit();

        mDoneBtn = (Button) rootView.findViewById(R.id.upload_remote_library_btn);
        mDoneBtn.setOnClickListener(onClickListener);
        if (mActivity.isChooseLibPage())
            mDoneBtn.setVisibility(View.VISIBLE);

        return rootView;
    }

    private View.OnClickListener onClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            mActivity.saveSettings();
            mActivity.finish();
        }
    };

    /**
     * Instantiates a new fragment if mSelectionFragment is null.
     * Returns the current fragment, otherwise.
     */
    public CloudLibrarySelectionFragment getAccountOrReposSelectionFragment() {
        if (mSelectionFragment == null) {
            mSelectionFragment = new CloudLibrarySelectionFragment();
        }

        return mSelectionFragment;
    }

}

