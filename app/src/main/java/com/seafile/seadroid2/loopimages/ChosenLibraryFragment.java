package com.seafile.seadroid2.loopimages;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ListView;

import com.google.common.collect.Lists;
import com.seafile.seadroid2.R;
import com.seafile.seadroid2.SettingsManager;
import com.seafile.seadroid2.account.Account;
import com.seafile.seadroid2.data.DataManager;
import com.seafile.seadroid2.ui.activity.SeafilePathChooserActivity;

import java.util.ArrayList;
import java.util.List;

/**
 * A simple {@link Fragment} subclass.
 */
public class ChosenLibraryFragment extends Fragment {
    private Button mDoneBtn, mAddBtn;

    private ListView chosenlibraryListView;
    private ChosenLibraryAdapter chosenlibraryListAdapter;
    private LoopImagesWidgetConfigureActivity mActivity;

    public ChosenLibraryFragment() {
        // Required empty public constructor
    }

    public void saveDirInfo(){
        List<String> dirInfoStrs = new ArrayList<String>();
        for(int i=0;i<chosenlibraryListAdapter.getCount();++i){
            dirInfoStrs.add(chosenlibraryListAdapter.getItem(i).toString());
        }
        SettingsManager.instance().setLoopImagesWidgetDirInfo(mActivity.getAppWidgetId(), dirInfoStrs);
    }

//    @Override
//    public void onSaveInstanceState(@NonNull Bundle outState) {
//        super.onSaveInstanceState(outState);
//    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        mActivity = (LoopImagesWidgetConfigureActivity) getActivity();
        Context context = mActivity.getApplicationContext();
        View rootView = mActivity.getLayoutInflater().inflate(R.layout.loop_images_widget_chosen_library_fragment, container, false);

        chosenlibraryListAdapter = new ChosenLibraryAdapter(this);

        mDoneBtn = (Button) rootView.findViewById(R.id.loopimages_chosen_library_done_btn);
        mDoneBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                saveDirInfo();
                AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
                LoopImagesWidget.updateAppWidget(context, appWidgetManager, mActivity.getAppWidgetId());

                Intent resultValue = new Intent();
                resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, mActivity.getAppWidgetId());
                mActivity.setResult(mActivity.RESULT_OK, resultValue);
                mActivity.finish();
            }
        });
        mAddBtn = (Button) rootView.findViewById(R.id.loopimages_chosen_library_add_btn);
        mAddBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(mActivity, RemoteLibrarySelectionActivity.class);
                startActivityForResult(intent, mActivity.CHOOSE_LOOPIMAGES_REQUEST);
            }
        });
        chosenlibraryListView = (ListView) rootView.findViewById(R.id.loopimages_chosen_library_lv);
        chosenlibraryListView.setAdapter(chosenlibraryListAdapter);

        List<DirInfo> dirInfos = mActivity.getDirInfo(mActivity.getAppWidgetId());
        List<DirInfo> checkedDirInfos = Lists.newArrayList();
        for(DirInfo dirInfo: dirInfos){
            if(dirInfo == null){
                continue;
            }
            Account account = LoopImagesWidgetConfigureActivity.getAccount(getContext(), dirInfo.getAccountSignature());
            if(account == null){
                continue;
            }
            DataManager dataManager = new DataManager(account);
            if(dataManager.getCachedRepoByID(dirInfo.getRepoId()) == null){
                continue;
            }
            checkedDirInfos.add(dirInfo);
        }

        if(checkedDirInfos.size() != dirInfos.size()){
            List<String> dirInfoStrs = new ArrayList<String>();
            for (DirInfo dirInfo : checkedDirInfos) {
                dirInfoStrs.add(dirInfo.toString());
            }
            SettingsManager.instance().setLoopImagesWidgetDirInfo(mActivity.getAppWidgetId(), dirInfoStrs);
        }

        chosenlibraryListAdapter.setDirs(checkedDirInfos);

        if (chosenlibraryListAdapter.getCount() > 0)
            mDoneBtn.setVisibility(View.VISIBLE);

        return rootView;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onActivityResult (int requestCode, int resultCode, Intent data){
        if(resultCode != mActivity.RESULT_OK){
            return;
        }
        final String repoName = data.getStringExtra(SeafilePathChooserActivity.DATA_REPO_NAME);
        final String repoId = data.getStringExtra(SeafilePathChooserActivity.DATA_REPO_ID);
        final String dirPath = data.getStringExtra(SeafilePathChooserActivity.DATA_DIRECTORY_PATH);
        final String dirId = data.getStringExtra(SeafilePathChooserActivity.DATA_DIR);
        final Account account = data.getParcelableExtra(SeafilePathChooserActivity.DATA_ACCOUNT);

        if (repoName != null && repoId != null && dirPath != null && dirId != null && account != null) {
            chosenlibraryListAdapter.addDir(new DirInfo(account.getSignature(), repoId, repoName, dirId, dirPath));
            if (chosenlibraryListAdapter.getCount() > 0)
                mDoneBtn.setVisibility(View.VISIBLE);
        }
    }
}