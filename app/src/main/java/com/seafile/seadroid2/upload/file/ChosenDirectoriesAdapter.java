package com.seafile.seadroid2.upload.file;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.common.collect.Lists;
import com.mcxtzhang.swipemenulib.SwipeMenuLayout;
import com.seafile.seadroid2.R;
import com.seafile.seadroid2.SeadroidApplication;
import com.seafile.seadroid2.account.Account;
import com.seafile.seadroid2.data.DataManager;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChosenDirectoriesAdapter extends BaseAdapter {

    private List<String> dirPaths;

    private Map<String, DataManager> accountDataManager;

    public ChosenDirectoriesAdapter() {
        dirPaths = Lists.newLinkedList();
        accountDataManager = new HashMap<String, DataManager>();
    }

    /** sort files type */
    public static final int SORT_BY_NAME = 9;
    /** sort files type */
    public static final int SORT_BY_LAST_MODIFIED_TIME = 10;
    /** sort files order */
    public static final int SORT_ORDER_ASCENDING = 11;
    /** sort files order */
    public static final int SORT_ORDER_DESCENDING = 12;

    private DataManager getDataManager(Account account){
        if (!accountDataManager.containsKey(account.getSignature())) {
            accountDataManager.put(account.getSignature(), new DataManager(account));
        }
        return accountDataManager.get(account.getSignature());
    }

    @Override
    public int getCount() {
        return dirPaths.size();
    }

    @Override
    public boolean isEmpty() {
        return dirPaths.isEmpty();
    }

    @Override
    public String getItem(int position) {
        return dirPaths.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    public List<String> getDirs(){
        return dirPaths;
    }

    public void clearDirs() {
        dirPaths.clear();
    }

    public void setDirs(List<String> dirs) {
        if(dirs == null){
            if(!this.dirPaths.isEmpty()) {
                this.dirPaths.clear();
                notifyDataSetChanged();
            }
            return;
        }
        clearDirs();
        this.dirPaths.addAll(dirs);
        notifyDataSetChanged();
    }

    public void addDir(String dir){
        this.dirPaths.add(dir);
        notifyDataSetChanged();
    }

    public void addDirs(List<String> dirs){
        if(dirs == null || dirs.size() == 0){
            return;
        }
        this.dirPaths.addAll(dirs);
        notifyDataSetChanged();
    }

    public void sortDirs(int order) {
        Collections.sort(dirPaths);
        if (order == SORT_ORDER_DESCENDING) {
            Collections.reverse(dirPaths);
        }
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        SwipeMenuLayout view = (SwipeMenuLayout) convertView;
        Viewholder viewHolder;
        String dir = this.dirPaths.get(position);

        if (convertView == null) {
            view = (SwipeMenuLayout) LayoutInflater.from(SeadroidApplication.getAppContext()).
                    inflate(R.layout.fuc_chosen_directory_list_item, null);
            TextView title = (TextView) view.findViewById(R.id.fuc_list_item_title);
            ImageView icon = (ImageView) view.findViewById(R.id.fuc_list_item_icon);
            Button action = (Button) view.findViewById(R.id.fuc_list_item_action);
            viewHolder = new Viewholder(title, icon, action);
            view.setTag(viewHolder);
        } else {
            view.quickClose();
            viewHolder = (Viewholder) convertView.getTag();
        }

        viewHolder.icon.setImageResource(R.drawable.folder);
        viewHolder.title.setText(dir);
        viewHolder.action.setTag(position);
        viewHolder.action.setOnClickListener(new DeleteItemOnClickListener());

        viewHolder.title.setTextColor(Color.BLACK);
        if (android.os.Build.VERSION.SDK_INT >= 11) {
            viewHolder.icon.setAlpha(255);
        }
        return view;
    }

    private class DeleteItemOnClickListener implements Button.OnClickListener{
        @Override
        public void onClick(View v) {
            Integer position = (Integer) v.getTag();
            deleteItem(position);
        }
    }

    private void deleteItem(int position){
        dirPaths.remove(position);
        notifyDataSetChanged();
    }

    private class Viewholder {
        TextView title;
        ImageView icon;
        Button action;

        public Viewholder(TextView title, ImageView icon, Button action) {
            super();
            this.icon = icon;
            this.title = title;
            this.action = action;
        }
    }
}
