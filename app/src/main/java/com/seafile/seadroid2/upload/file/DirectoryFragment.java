package com.seafile.seadroid2.upload.file;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.seafile.seadroid2.R;

/**
 * Buckets fragment
 */
public class DirectoryFragment extends Fragment {
    private FragmentManager fm;
    private FileUploadConfigActivity mActivity;

    private Fragment currentFragment;
    private Fragment targetFragment;
    private LocalDirectorySelectionFragment mSelectionFragment;

    private boolean isInSelectedFragment;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        mActivity = (FileUploadConfigActivity) getActivity();
        View rootView = mActivity.getLayoutInflater().inflate(R.layout.fuc_local_directory_fragment, null);

        fm = getChildFragmentManager();
        fm.beginTransaction()
                .add(R.id.fuc_local_directory_list_container, currentFragment)
                .commit();
        isInSelectedFragment = true;
        return rootView;
    }

    public void initFragments(LocalDirectorySelectedFragment selectedFragment, LocalDirectorySelectionFragment selectionFragment){
        currentFragment = selectedFragment;
        targetFragment = selectionFragment;
        mSelectionFragment = selectionFragment;
    }

    public void switchFragment(){
        if(!targetFragment.isAdded()) {
            fm.beginTransaction().hide(currentFragment).add(R.id.fuc_local_directory_list_container, targetFragment).commit();
        }else{
            fm.beginTransaction().hide(currentFragment).show(targetFragment).commit();
        }
        Fragment temp = targetFragment;
        targetFragment = currentFragment;
        currentFragment = temp;
        isInSelectedFragment = !isInSelectedFragment;
    }

    public boolean stepBack(){
        if(isInSelectedFragment){
            return false;
        }
        if(!mSelectionFragment.backPress()){
            switchFragment();
        }
        return true;
    }
}
