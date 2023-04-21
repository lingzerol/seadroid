package com.seafile.seadroid2.upload.file;

import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import com.seafile.seadroid2.R;

import org.w3c.dom.Text;

import java.util.List;

/**
 * Buckets fragment
 */
public class LocalDirectorySelectedFragment extends Fragment {
    public static final int CHOOSE_DIRECTORY_REQUEST = 1;

    private Button mAddBtn, mDoneBtn;

    private ListView chosenDirectoriesListView;
    private ChosenDirectoriesAdapter chosenDirectoriesListAdapter;
    private FileUploadConfigActivity mActivity;

    public LocalDirectorySelectedFragment() {
        // Required empty public constructor
    }

    public List<String> getChosenDirs(){
        return  chosenDirectoriesListAdapter.getDirs();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        mActivity = (FileUploadConfigActivity) getActivity();
        Context context = mActivity.getApplicationContext();
        View rootView = mActivity.getLayoutInflater().inflate(R.layout.fuc_local_directory_selected_fragment, container, false);

        chosenDirectoriesListAdapter = new ChosenDirectoriesAdapter();

        mDoneBtn = (Button) rootView.findViewById(R.id.fuc_selected_done_btn);
        mDoneBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mActivity.saveSettings();
                mActivity.finish();
            }
        });
        mAddBtn = (Button) rootView.findViewById(R.id.fuc_selected_add_btn);
        mAddBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mActivity.switchDirectoryFragment();
            }
        });

        chosenDirectoriesListView = (ListView) rootView.findViewById(R.id.fuc_selected_lv);
        chosenDirectoriesListView.setAdapter(chosenDirectoriesListAdapter);

        List<String> dirPaths = mActivity.getChosenDirectories();

        chosenDirectoriesListAdapter.setDirs(dirPaths);

        if (chosenDirectoriesListAdapter.getCount() > 0)
            mDoneBtn.setVisibility(View.VISIBLE);

        return rootView;
    }

    public Fragment getFragment(){
        return this;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    public void addDirs(List<String> paths){
        if(paths == null || paths.size() == 0){
            return;
        }
        chosenDirectoriesListAdapter.addDirs(paths);
        if (chosenDirectoriesListAdapter.getCount() > 0)
            mDoneBtn.setVisibility(View.VISIBLE);
    }

    public void addDir(String path){
        if(path == null || path.length() == 0){
            return;
        }
        chosenDirectoriesListAdapter.addDir(path);
        if (chosenDirectoriesListAdapter.getCount() > 0)
            mDoneBtn.setVisibility(View.VISIBLE);
    }

    public List<String> getDirs(){
        return chosenDirectoriesListAdapter.getDirs();
    }
}
