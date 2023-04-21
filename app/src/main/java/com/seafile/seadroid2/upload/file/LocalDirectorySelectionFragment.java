package com.seafile.seadroid2.upload.file;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.google.common.collect.Lists;
import com.seafile.seadroid2.R;
import com.seafile.seadroid2.SeadroidApplication;
import com.seafile.seadroid2.util.ConcurrentAsyncTask;

import org.w3c.dom.Text;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class LocalDirectorySelectionFragment extends Fragment {

    public static final String DEBUG_TAG = "LocalDirectorySelectionActivity";

    public static final String SELECTED_LOCAL_DIRECTORY_NAMES = "selectedLocalDirectoryNames";
    public static final String SELECTED_LOCAL_PARENT_PATH = "selectedLocalParentPath";

    public static final String DATA_REPO_ID = "repoID";
    public static final String DATA_REPO_NAME = "repoNAME";
    public static final String DATA_DIR = "dir";
    public static final String DATA_ACCOUNT = "account";
    private static final String EXTERNAL_STORAGE_PATH = Environment.getExternalStorageDirectory().getAbsolutePath();

    private LoadDirTask mLoadDirTask;
    private LocalDirectoryAdapter mDirectoryAdapter;

    private RelativeLayout mUpLayout;
    private TextView mCurrentFolderText;
    private TextView mEmptyText, mErrorText;
    private ImageView mRefreshBtn;
    private View mProgressContainer, mListContainer;
    private ListView mFoldersListView;
    private Button mDoneButton;
    //    private Cursor mCursor;
    private String mCurrentDir = EXTERNAL_STORAGE_PATH;
    FileUploadConfigActivity mActivity;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mActivity = (FileUploadConfigActivity) getActivity();
        Context context = mActivity.getApplicationContext();
        View rootView = mActivity.getLayoutInflater().inflate(R.layout.fuc_local_directory_selection_fragment, container, false);

//        Intent intent = getIntent();
//        Account account = intent.getParcelableExtra("account");
        mDirectoryAdapter = new LocalDirectoryAdapter();
        mFoldersListView = (ListView) rootView.findViewById(R.id.fuc_selection_lv);
        mFoldersListView.setFastScrollEnabled(true);
        setListAdapter(mDirectoryAdapter);
        mUpLayout = (RelativeLayout) rootView.findViewById(R.id.fuc_selection_up_layout);
        mCurrentFolderText = (TextView) rootView.findViewById(R.id.fuc_selection_current_directory_txt);
        mEmptyText = (TextView) rootView.findViewById(R.id.fuc_selection_empty_msg);
        mErrorText = (TextView) rootView.findViewById(R.id.fuc_selection_error_msg);
        mRefreshBtn = (ImageView) rootView.findViewById(R.id.fuc_selection_refresh_iv);
        mProgressContainer = rootView.findViewById(R.id.fuc_selection_progress_container);
        mListContainer = rootView.findViewById(R.id.fuc_selection_list_container);
        mDoneButton = (Button) rootView.findViewById(R.id.fuc_local_directory_done_btn);
        mRefreshBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                refreshList();
            }
        });

        mUpLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    stepBack();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        mFoldersListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                onListItemClick(parent, position, id);
            }
        });
        mFoldersListView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                return onListLongItemClick(parent, position, id);
            }
        });

        mDoneButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                List<String> dirNames = mDirectoryAdapter.getSelectedDirname();
                if(dirNames == null || dirNames.size() == 0){
                    return;
                }
                List<String> paths = Lists.newArrayList();
                for(String dirname:dirNames){
                    paths.add(mCurrentDir + "/" + dirname);
                }
                mActivity.addSelectedLocalDirectory(paths);
            }
        });

        chooseDir(mCurrentDir);

        return rootView;
    }

    @Override
    public void onResume() {
        super.onResume();
    }

//    @Override
//    public void onBackPressed() {
//        if(mDirectoryAdapter.getCheckBoxVisibility() == View.VISIBLE){
//            mDirectoryAdapter.setCheckBoxesVisibility(View.GONE);
//            mDoneButton.setVisibility(View.GONE);
//        }else if(!mCurrentDir.equals(EXTERNAL_STORAGE_PATH) && mCurrentDir.length() > EXTERNAL_STORAGE_PATH.length()) {
//            stepBack();
//        }
//        else{
//            Intent intent = new Intent();
//            setResult(RESULT_OK, intent);
//            finish();
//        }
//    }

    private void refreshList() {
        chooseDir(mCurrentDir);
    }

    public void onListItemClick(View v, final int position, final long id) {
        if(mDirectoryAdapter.getCheckBoxVisibility() == View.VISIBLE) {
            mDirectoryAdapter.clickCheckBox(position);
        }else {
            chooseDir(mCurrentDir + "/" + mDirectoryAdapter.subDirs.get(position));
        }
        if(mDirectoryAdapter.getCheckNum() > 0){
            mDoneButton.setVisibility(View.VISIBLE);
        }else{
            mDoneButton.setVisibility(View.GONE);
        }
    }

    public boolean onListLongItemClick(View v, final int position, final long id) {
        CheckBox checkBox = (CheckBox) v.findViewById(R.id.fuc_directory_list_item_cb);
        mDirectoryAdapter.setCheckBoxesVisibility(View.VISIBLE);
        mDirectoryAdapter.clickCheckBox(position);
        if(mDirectoryAdapter.getCheckNum() > 0){
            mDoneButton.setVisibility(View.VISIBLE);
        }else{
            mDoneButton.setVisibility(View.GONE);
        }
        return true;
    }

    public boolean stepBack() {
        if(mCurrentDir.equals(EXTERNAL_STORAGE_PATH)){
            return false;
        }
        int index = mCurrentDir.lastIndexOf("/");
        mCurrentDir =  mCurrentDir.substring(0, index);
        chooseDir(mCurrentDir);
        mDoneButton.setVisibility(View.GONE);
        return true;
    }

    public boolean backPress(){
        if(mDirectoryAdapter.getCheckBoxVisibility() == View.VISIBLE) {
            mDirectoryAdapter.setCheckBoxesVisibility(View.GONE);
            mDoneButton.setVisibility(View.GONE);
            return true;
        }else{
            return stepBack();
        }
    }

    public boolean isInRoot(){
        return mCurrentDir.equals(EXTERNAL_STORAGE_PATH) || mCurrentDir.length() < EXTERNAL_STORAGE_PATH.length();
    }

    private void chooseDir(String path) {
        mEmptyText.setText(R.string.dir_empty);
        if(path.equals(EXTERNAL_STORAGE_PATH)){
            mUpLayout.setVisibility(View.GONE);
        }else{
            mUpLayout.setVisibility(View.VISIBLE);
        }
        refreshDir(path);
    }


    private void refreshDir(String path) {
        mLoadDirTask = new LoadDirTask(path);
        ConcurrentAsyncTask.execute(mLoadDirTask);
    }

    private void showListOrEmptyText(int listSize) {
        if (listSize == 0) {
            mFoldersListView.setVisibility(View.GONE);
            mEmptyText.setVisibility(View.VISIBLE);
        } else {
            mFoldersListView.setVisibility(View.VISIBLE);
            mEmptyText.setVisibility(View.GONE);
        }
    }

    private void setListAdapter(BaseAdapter adapter) {
        mFoldersListView.setAdapter(adapter);
    }

    private void setCurrentDirText(String text) {
        mCurrentFolderText.setText(text);
    }


//    @Override
//    public void onDestroyView() {
//        super.onDestroyView();
//
//        if (isRemoving()) {
//            mCursor.close();
//            mCursor = null;
//        }
//
//    }


    private class LoadDirTask extends AsyncTask<Void, Void, List<String>> {
        private String path;
        private Exception err;

        public LoadDirTask(String path) {
            this.path = path;
        }

        @Override
        protected void onPreExecute() {
            showLoading(true);
        }

        @Override
        protected List<String> doInBackground(Void... params) {
            try {
                List<String> subDirs = Lists.newArrayList();
                File file = new File(path);
                if(file.isDirectory()){
                    File[] subFiles = file.listFiles();
                    if(subFiles != null && subFiles.length > 0) {
                        for (int i = 0; i < subFiles.length; ++i) {
                            if (subFiles[i] != null && subFiles[i].isDirectory()) {
                                subDirs.add(subFiles[i].getName());
                            }
                        }
                    }
                }
                return subDirs;
            } catch (Exception e) {
                err = e;
            }
            return null;
        }

        @SuppressLint("LongLogTag")
        @Override
        protected void onPostExecute(List<String> subDirs) {
            showLoading(false);
            if(subDirs == null){
                setErrorMessage("Directory failed to access.");
                return;
            }
            showListOrEmptyText(subDirs.size());
            mDirectoryAdapter.setDirs(subDirs);
            mCurrentFolderText.setText(this.path);
            mCurrentDir = this.path;
        }
    }

    private void setErrorMessage(int resID) {
        //mContentArea.setVisibility(View.GONE);
        mErrorText.setVisibility(View.VISIBLE);
        mErrorText.setText(getString(resID));
    }

    private void setErrorMessage(String errorMessage) {
        //mContentArea.setVisibility(View.GONE);
        mErrorText.setVisibility(View.VISIBLE);
        mErrorText.setText(errorMessage);
    }

    private void clearError() {
        mErrorText.setVisibility(View.GONE);
        //mContentArea.setVisibility(View.VISIBLE);
    }

    private void showLoading(boolean loading) {
        clearError();
        if (loading) {
            mProgressContainer.startAnimation(AnimationUtils.loadAnimation(
                    mActivity, android.R.anim.fade_in));
            mListContainer.startAnimation(AnimationUtils.loadAnimation(
                    mActivity, android.R.anim.fade_out));

            mProgressContainer.setVisibility(View.VISIBLE);
            mListContainer.setVisibility(View.INVISIBLE);
        } else {
            mProgressContainer.startAnimation(AnimationUtils.loadAnimation(
                    mActivity, android.R.anim.fade_out));
            mListContainer.startAnimation(AnimationUtils.loadAnimation(
                    mActivity, android.R.anim.fade_in));

            mProgressContainer.setVisibility(View.GONE);
            mListContainer.setVisibility(View.VISIBLE);
        }
    }

    public class LocalDirectoryAdapter extends BaseAdapter {

        public List<String> subDirs;
        public List<View> views;
        private int visible;
        private int checkNum;

        LocalDirectoryAdapter(){
            super();
            subDirs = Lists.newArrayList();
            views = Lists.newArrayList();
            visible = View.GONE;
            checkNum = 0;
        }

        @Override
        public int getCount() {
            return subDirs.size();
        }

        @Override
        public boolean isEmpty() {
            return subDirs.isEmpty();
        }

        @Override
        public String getItem(int position) {
            return subDirs.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        public List<String> getSelectedDirname(){
            List<String> selected = new ArrayList<String>();
            for(int i=0;i<views.size();++i){
                CheckBox checkBox = (CheckBox) views.get(i).findViewById(R.id.fuc_directory_list_item_cb);
                if(checkBox.isChecked()){
                    selected.add(subDirs.get(i));
                }
            }
            return selected;
        }

        public void clearDirs() {
            this.subDirs.clear();
            this.views.clear();
            this.visible = View.GONE;
            this.checkNum = 0;
        }

        public void setDirs(List<String> dirList){
            this.clearDirs();
            this.subDirs.addAll(dirList);
            for(String dirname: this.subDirs){
                View view = (RelativeLayout) LayoutInflater.from(SeadroidApplication.getAppContext()).
                        inflate(R.layout.fuc_directory_list_item, null);
                TextView title = (TextView) view.findViewById(R.id.fuc_directory_list_item_title_tv);
                CheckBox checkBox = (CheckBox) view.findViewById(R.id.fuc_directory_list_item_cb);
                Viewholder viewHolder = new Viewholder(title, checkBox);
                view.setTag(viewHolder);
                viewHolder.title.setText(dirname);
                this.views.add(view);
            }
            notifyDataSetChanged();
        }

        public int getCheckNum(){
            return checkNum;
        }

        public int getCheckBoxVisibility(){
            return visible;
        }

        public void clickCheckBox(int position){
            if(position >= this.getCount()){
                return;
            }
            View view = this.views.get(position);
            CheckBox checkBox = (CheckBox) view.findViewById(R.id.fuc_directory_list_item_cb);
            if(checkBox.isChecked()) {
                --this.checkNum;
                checkBox.setChecked(false);
            }else {
                ++this.checkNum;
                checkBox.setChecked(true);
            }
        }

        public void setCheckBoxesVisibility(int value){
            if(visible == value){
                return;
            }
            visible = value;
            for(View view: this.views){
                CheckBox checkBox = (CheckBox) view.findViewById(R.id.fuc_directory_list_item_cb);
                checkBox.setVisibility(value);
                if(value != View.VISIBLE){
                    checkBox.setChecked(false);
                }
            }
            if(value != View.VISIBLE){
                checkNum = 0;
            }
//            notifyDataSetChanged();
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            return this.views.get(position);
        }
        private class Viewholder {
            TextView title;
            CheckBox checkBox;

            public Viewholder(TextView title, CheckBox checkBox) {
                super();
                this.title = title;
                this.checkBox = checkBox;
            }
        }
    }
}
