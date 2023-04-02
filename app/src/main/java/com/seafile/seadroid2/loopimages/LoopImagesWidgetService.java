package com.seafile.seadroid2.loopimages;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.SystemClock;
import android.support.annotation.RequiresApi;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.RemoteViews;

import com.google.common.collect.Lists;
import com.seafile.seadroid2.R;
import com.seafile.seadroid2.SettingsManager;
import com.seafile.seadroid2.account.Account;
import com.seafile.seadroid2.account.AccountManager;
import com.seafile.seadroid2.data.DataManager;
import com.seafile.seadroid2.data.DirentCache;
import com.seafile.seadroid2.data.SeafDirent;
import com.seafile.seadroid2.transfer.DownloadTaskInfo;
import com.seafile.seadroid2.transfer.TaskState;
import com.seafile.seadroid2.transfer.TransferService;
import com.seafile.seadroid2.util.ConcurrentAsyncTask;
import com.seafile.seadroid2.util.Utils;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;


public class LoopImagesWidgetService extends Service {

    private static final int ALARM_DURATION = 1 * 60 * 1000;
    private static final int UPDATE_DURATION = 5 * 1000;
    private static final int UPDATE_MESSAGE = 1000;
    private static final int CHECKT_DOWNLOAD_STATE = 1001;
    private static final int CHECKT_DOWNLOAD_STATE_DURATION = 5*1000;
    private static final int UPDATE_IMAGE_DELAY = 60 * 60 * 1000;
    private static final int ONCE_UPLOAD_IMAGE_NUM = 3;
    private static final int ONCE_REMOVE_IMAGE_NUM = 3*ONCE_UPLOAD_IMAGE_NUM;
    private static final int MAX_ACCESS_TIME = 5;
    private static final int MAX_DOWNLOAD_IMAGE_NUM = 500;
    public static final String UPDATE_WIDGETS_KEY = "update_widgets_key";
    public static final int UPDATE_ALL_WIDGETS = -1;
    public static final int UPDATE_NONE_WIDGETS = -2;
    public static final String DELETE_WIDGETS_KEY = "delete_widgets_key";
    public static final int DELETE_ALL_WIDGETS = -1;
    public static final int DELETE_NONE_WIDGETS = -2;
    public static final String UPDATE_IMAGE_INFO_SIGNAL = "update_image_info_signal";
    public static final String DELETE_IMAGE_INFO_SIGNAL = "delete_image_info_signal";
    public static final String DELAY_UPDATE_ALL_SIGNAL = "delay_update_signal";
    private static final String DEBUG_TAG = "LoopImagesWidgetService";

    private Object updateWidgetLock = new Object();
    private Object imageInfosLock = new Object();
    private LinkedList<TaskInfo> tasksInProgress = Lists.newLinkedList();
    private Random rand = new Random();
    private LoopImagesDBHelper dbHelper = LoopImagesDBHelper.getInstance();

    private Map<String, DataManager> dataManagers = new HashMap<String, DataManager>();
    private Map<String, Account> accounts = new HashMap<String, Account>();

    private boolean isUpdatingInfo = false;
    private Object updatingInfoLock = new Object();

    private int imageInfoDelayTimes = 0;

    private static final String[] FILE_SUFIX = new String[]{
            "gif",
            "png",
            "bmp",
            "jpeg",
            "jpg",
            "webp"
    };

    private TransferService txService = null;

    private UpdateHandler updateHandler;

    ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            TransferService.TransferBinder binder = (TransferService.TransferBinder) service;
            synchronized (LoopImagesWidgetService.this) {
                txService = binder.getService();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {

            synchronized (LoopImagesWidgetService.this) {
                txService = null;
            }
        }
    };

    private synchronized void startTransferService() {
        if (txService != null)
            return;

        Intent bIntent = new Intent(getApplicationContext(), TransferService.class);
        getApplicationContext().bindService(bIntent, mConnection, Context.BIND_AUTO_CREATE);
    }

    private synchronized void stopTransferService(){
        if(mConnection == null){
            return;
        }
        getApplicationContext().unbindService(mConnection);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void sendAlaramMessage(){
        AlarmManager manager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent alarmIntent = new Intent(getBaseContext(), LoopImagesWidgetService.class);
        PendingIntent pendingIntent = PendingIntent.getService(getBaseContext(), 0,
                alarmIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        manager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + ALARM_DURATION, pendingIntent);
    }

    private void addUpdateInfoTask(int updateSignal, int deleteSignal){
        synchronized (updatingInfoLock){
            if(!isUpdatingInfo) {
                isUpdatingInfo = true;
                ConcurrentAsyncTask.execute(new UpdateImageInfoTask(updateSignal, deleteSignal));
            }
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(DEBUG_TAG, "onStartCommand");

        int updateAppWidgetSignal = UPDATE_NONE_WIDGETS;
        if(intent!=null) {
            updateAppWidgetSignal = intent.getIntExtra(UPDATE_IMAGE_INFO_SIGNAL, UPDATE_NONE_WIDGETS);
        }
        if(updateAppWidgetSignal != UPDATE_NONE_WIDGETS){
            addUpdateInfoTask(updateAppWidgetSignal, DELETE_NONE_WIDGETS);
            return START_STICKY;
        }

        int deleteAppWidgetSignal = DELETE_NONE_WIDGETS;
        if(intent != null){
            deleteAppWidgetSignal = intent.getIntExtra(DELETE_IMAGE_INFO_SIGNAL, DELETE_NONE_WIDGETS);
        }
        if(deleteAppWidgetSignal != DELETE_NONE_WIDGETS){
            addUpdateInfoTask(UPDATE_NONE_WIDGETS, deleteAppWidgetSignal);
            return START_STICKY;
        }

        boolean delayUpdateAll = false;
        if(intent != null) {
            delayUpdateAll = intent.getBooleanExtra(DELAY_UPDATE_ALL_SIGNAL, false);
        }
        if(delayUpdateAll){
            AlarmManager manager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            Intent alarmIntent = new Intent(getBaseContext(), LoopImagesWidgetService.class);
            alarmIntent.putExtra(UPDATE_IMAGE_INFO_SIGNAL, UPDATE_ALL_WIDGETS);
            PendingIntent pendingIntent = PendingIntent.getService(getBaseContext(), 0,
                    alarmIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            manager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    SystemClock.elapsedRealtime() + 60*1000, pendingIntent);
            return START_STICKY;
        }

        if(imageInfoDelayTimes < UPDATE_IMAGE_DELAY / ALARM_DURATION){
            ++imageInfoDelayTimes;
        }else{
            imageInfoDelayTimes = 0;
        }

        if(imageInfoDelayTimes == 1){
            addUpdateInfoTask(UPDATE_ALL_WIDGETS, DELETE_NONE_WIDGETS);
        }

        sendAlaramMessage();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopTransferService();
    }

    @Override
    public void onCreate() {
        super.onCreate();

        Log.d(DEBUG_TAG, "onCreate");

        if(updateHandler == null) {
            updateHandler = new UpdateHandler();
        }

        Message message = updateHandler.obtainMessage();
        message.what = UPDATE_MESSAGE;
        updateHandler.sendMessageDelayed(message, UPDATE_DURATION);

        startTransferService();
        createNotificationChannel();
        sendAlaramMessage();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            String CHANNEL_ID = "seafile_channel";
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    "seafile notification",
                    NotificationManager.IMPORTANCE_DEFAULT);
            ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).createNotificationChannel(channel);
            Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle("")
                    .setContentText("").build();
            startForeground(1, notification);
        }
    }

    private void downloadImages(int appWidgetId, ImageInfo imageInfo){
        if(imageInfo == null || imageInfo.getDirInfo() == null){
            return;
        }
        File file = imageInfo.getFile();
        if(file != null && file.exists()){
            if(getFileSize(file) == imageInfo.getFileSize()) {
                dbHelper.setImageDownloadState(imageInfo.getDirInfo().getAccountSignature(), imageInfo.getDirInfo().getRepoId(), imageInfo.getDirInfo().getDirId(), imageInfo.getFilePath(), true);
                return;
            }else{
                imageInfo.deleteStorage();
            }
        }
        Account account = LoopImagesWidgetConfigureActivity.getAccount(getApplicationContext(), imageInfo.getDirInfo().getAccountSignature());
        if(account == null){
            return;
        }
        int taskID = txService.addDownloadTask(account,
                imageInfo.getDirInfo().getRepoName(),
                imageInfo.getDirInfo().getRepoId(),
                imageInfo.getRemoteFilePath(),
                imageInfo.getFileSize());
        tasksInProgress.add(new TaskInfo(taskID, appWidgetId, imageInfo));
    }

    private long getFileSize(File file) {
        long size = 0;
        try {
            if (file.exists()) {
                FileInputStream fis = null;
                fis = new FileInputStream(file);
                size = fis.available();
            }
        }catch (Exception e){
            return 0;
        }
        return size;
    }

    private void checkDownloadTasks(){
        Log.d(DEBUG_TAG, "checkDownloadTasks");
        int removeNum = 0;
        for (TaskInfo taskInfo: tasksInProgress) {
            DownloadTaskInfo info = txService.getDownloadTaskInfo(taskInfo.taskID);
            if(info == null){
                ++removeNum;
                continue;
            }
            ImageInfo item = taskInfo.imageInfo;
            if (info.err != null) {
                txService.removeDownloadTask(taskInfo.taskID);
                ++removeNum;
                item.deleteStorage();
                Utils.utilsLogInfo(true, "=======Task " + Integer.toString(taskInfo.taskID) + " failed to download " + info.localFilePath + "!!!");
                continue;
            }
            if (info.state == TaskState.INIT || info.state == TaskState.TRANSFERRING) {
                break;
            }
            ++removeNum;
            if(item == null || item.getDirInfo() == null){
                continue;
            }
            boolean haveSameSize = getFileSize(item.getFile())
                    == item.getFileSize();
            if (info.state == TaskState.FINISHED && haveSameSize) {
                dbHelper.setImageDownloadState(item.getDirInfo().getAccountSignature(), item.getDirInfo().getRepoId(), item.getDirInfo().getDirId(), item.getFilePath(), true);
//                if (queues.get(taskInfo.appWidgetId).getCount() >= LEFT_FOLLOW_UP_MAX_IMAGES_NUM) {
//                    shouldDownloads.put(taskInfo.appWidgetId, false);
//                }
            } else {
                item.deleteStorage();
            }
            txService.removeDownloadTask(taskInfo.taskID);
        }
        while(removeNum > 0 && !tasksInProgress.isEmpty()){
            --removeNum;
            tasksInProgress.removeFirst();
        }
    }

    private RemoteViews setRemoteViewOnClickActivity(RemoteViews views, int appWidgetId, String dirInfo, String imageName){
        Intent activityIntent = new Intent(getApplicationContext(), LoopImagesWidgetConfigureActivity.class);
        activityIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        activityIntent.putExtra(LoopImagesWidget.DIR_INFO, dirInfo);
        activityIntent.putExtra(LoopImagesWidget.IMAGE_NAME, imageName);
        PendingIntent widgetIntent = PendingIntent.getActivity(getApplicationContext(), appWidgetId, activityIntent, PendingIntent.FLAG_CANCEL_CURRENT);
        views.setOnClickPendingIntent(R.id.loopimages_widget_relative_layout, widgetIntent);
        return views;
    }

    private RemoteViews getDefalutRemoteViews(int appWidgetId){
        Context context = getApplicationContext();
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.loop_images_widget);
        views.setImageViewResource(R.id.loopimages_imageview, R.drawable.rem);
        views = setRemoteViewOnClickActivity(views, appWidgetId, null, null);
        return views;
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private Map<Integer, RemoteViews> updateWidget() {
        Map<Integer, RemoteViews> results = new HashMap<Integer, RemoteViews>();
        synchronized (imageInfosLock) {
            checkDownloadTasks();
//            Utils.utilsLogInfo(true, "=======Update Widget.");
            Context context = getApplicationContext();
            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
            int appWidgetIds[] = appWidgetManager.getAppWidgetIds(new ComponentName(context, LoopImagesWidget.class));
            if (appWidgetIds.length <= 0) {
                addUpdateInfoTask(UPDATE_ALL_WIDGETS, DELETE_NONE_WIDGETS);
                Message message = updateHandler.obtainMessage();
                message.what = UPDATE_MESSAGE;
                updateHandler.sendMessageDelayed(message, UPDATE_DURATION);
                return results;
            }
            int n = appWidgetIds.length;
            for (int appWidgetId : appWidgetIds) {
//                Utils.utilsLogInfo(true, "=======Loopimages widget " + appWidgetId + " update start.");
                boolean enableDownload = Utils.isWiFiOn() || LoopImagesWidgetConfigureActivity.getDataPlanAllowed(appWidgetId);
                if (Utils.isNetworkOn() && enableDownload && tasksInProgress.size() < ONCE_UPLOAD_IMAGE_NUM * 2 * n) {
                    if(dbHelper.getImageNum(appWidgetId, true) < MAX_DOWNLOAD_IMAGE_NUM){
                        Log.d(DEBUG_TAG, "Download new images.");
                        int addTaskNum = ONCE_UPLOAD_IMAGE_NUM;
                        int notDownloadedNum = dbHelper.getImageNum(appWidgetId, false);
                        while (addTaskNum > 0 && notDownloadedNum > 0) {
                            ImageInfo imageInfo = dbHelper.getImageInfo(appWidgetId, rand.nextInt(notDownloadedNum), false);
                            downloadImages(appWidgetId, imageInfo);
                            --addTaskNum;
                        }
                    }
                }
                if (dbHelper.getImageNum(appWidgetId, true) == 0) {
                    RemoteViews views = getDefalutRemoteViews(appWidgetId);
                    results.put(appWidgetId, views);
//                    appWidgetManager.updateAppWidget(appWidgetId, views);
                    continue;
                }
                boolean flag = false;
                int downloadedNum = 0;
                while ((downloadedNum = dbHelper.getImageNum(appWidgetId, true)) > 0) {
                    ImageInfo item = dbHelper.getImageInfo(appWidgetId, rand.nextInt(downloadedNum), true);
                    if(item == null){
                        Utils.utilsLogInfo(true, "Get null imageInfo from LoopImageWidgerDBHelper.getImageInfo");
                        break;
                    }
                    dbHelper.updateWidgetImageAccessCount(appWidgetId, item.getDirInfo().getAccountSignature(), item.getDirInfo().getRepoId(), item.getDirInfo().getDirId(), item.getFilePath(), 1);
                    Bitmap image = item.getBitMap();
                    if (image == null) {
                        item.deleteStorage();
                        dbHelper.setImageDownloadState(item.getDirInfo().getAccountSignature(), item.getDirInfo().getRepoId(), item.getDirInfo().getDirId(), item.getFilePath(), false);
                        continue;
                    }
                    Log.d(DEBUG_TAG, "Set new images.");
                    RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.loop_images_widget);
                    views.setImageViewBitmap(R.id.loopimages_imageview, image);
                    views = setRemoteViewOnClickActivity(views, appWidgetId, item.getDirInfo().toString(), Utils.fileNameFromPath(item.getFilePath()));
                    results.put(appWidgetId, views);
//                    appWidgetManager.updateAppWidget(appWidgetId, views);
                    flag = true;
                    break;
                }
                if (!flag) {
                    RemoteViews views = getDefalutRemoteViews(appWidgetId);
                    results.put(appWidgetId, views);
//                    appWidgetManager.updateAppWidget(appWidgetId, views);
                }
                if(Utils.isNetworkOn() && enableDownload && dbHelper.getImageNum(appWidgetId, true) >= MAX_DOWNLOAD_IMAGE_NUM) {
                    dbHelper.removeWidgetImages(appWidgetId, MAX_ACCESS_TIME);
                }
//                Utils.utilsLogInfo(true, "=======Loopimages widget "+appWidgetId + " update end.");
            }
        }
        Message message = updateHandler.obtainMessage();
        message.what = UPDATE_MESSAGE;
        updateHandler.sendMessageDelayed(message, UPDATE_DURATION);
        return results;
    }

    private void updateImageInfo(int updateWidgetSignal){
        if(updateWidgetSignal == UPDATE_NONE_WIDGETS){
            return;
        }
        synchronized (updateWidgetLock) {

            Utils.utilsLogInfo(true, "=======Loopimages widget update start.");
            Context context = getApplicationContext();
            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);

            int appWidgetIds[];
            if (updateWidgetSignal != UPDATE_ALL_WIDGETS) {
                appWidgetIds = new int[]{updateWidgetSignal};
            } else {
                appWidgetIds = appWidgetManager.getAppWidgetIds(new ComponentName(context, LoopImagesWidget.class));
            }


            Set<String> dirInfoSets = new HashSet<String>();
            for (int appWidgetId : appWidgetIds) {
                boolean haveUpdated = false;
                List<Integer> usedDirIds = Lists.newArrayList();
                List<DirInfo> dirInfos = Lists.newArrayList();
                for (DirInfo info : LoopImagesWidgetConfigureActivity.getDirInfo(appWidgetId)) {
                    if (info == null) {
                        haveUpdated = true;
                        continue;
                    }
                    DataManager dataManager = getDataManager(info.getAccountSignature());
                    if(dataManager == null){
                        haveUpdated = true;
                        continue;
                    }
                    int dirInfoID = -1;
                    String dirID = dataManager.getDirID(info.getRepoId(), info.getDirPath());
                    if (!dirID.equals(info.getDirId())) {
                        if(!dirInfoSets.contains(info.toString())) {
//                        synchronized (imageInfosLock) {
//                            deleteDirs.add(dbHelper.getDirInfoID(info.getAccountSignature(), info.getRepoId(), info.getDirId()));
////                            dbHelper.deleteAllWidgetDir(info.getAccountSignature(), info.getRepoId(), info.getDirId());
//                        }
//                        synchronized (imageInfosLock){
//                            dbHelper.deleteWidgetDir(appWidgetId, info.getAccountSignature(), info.getRepoId(), info.getDirId());
//                        }
                            synchronized (imageInfosLock) {
                                dirInfoID = dbHelper.getDirInfoID(info.getAccountSignature(), info.getRepoId(), info.getDirId());
                            }
                            if(dirInfoID >= 0){
                                synchronized (imageInfosLock) {
                                    dbHelper.updateDirInfoDirID(dirInfoID, dirID);
                                }
                                synchronized (imageInfosLock){
                                    dbHelper.setDirImagePreserve(dirInfoID, false);
                                }
                                if(dataManager != null) {
                                    List<SeafDirent> seafDirents = dataManager.getCachedDirents(info.getRepoId(), info.getDirPath());
                                    for (SeafDirent dirent : seafDirents) {
                                        synchronized (imageInfosLock) {
                                            dbHelper.addImage(dirInfoID, Utils.pathJoin(dataManager.getRepoDir(info.getRepoName(), info.getRepoId()), info.getDirPath(), dirent.name), dirent.size);
                                        }
                                    }
                                    synchronized (imageInfosLock) {
                                        dbHelper.deleteDirUnPreserveImage(dirInfoID);
                                    }
                                }
                            }
                            dirInfoSets.add(info.toString());
                        }
                        info.setDirId(dirID);
                        haveUpdated = true;
                    }
                    synchronized (imageInfosLock) {
                        dirInfoID = dbHelper.widgetContainDir(appWidgetId, info.getAccountSignature(), info.getRepoId(), info.getDirId());
                    }
                    if (dirInfoID >= 0) {
                        usedDirIds.add(dirInfoID);
                        dirInfos.add(info);
                        continue;
                    }
                    synchronized (imageInfosLock) {
                        dirInfoID = dbHelper.getDirInfoID(info.getAccountSignature(), info.getRepoId(), info.getDirId());
                    }
                    if (dirInfoID < 0) {
                        if(dataManager != null) {
                            List<SeafDirent> seafDirents = dataManager.getCachedDirents(info.getRepoId(), info.getDirPath());
                            synchronized (imageInfosLock) {
                                dirInfoID = dbHelper.addDirInfo(info.getAccountSignature(), info.getRepoId(), info.getRepoName(), info.getDirId(), info.getDirPath());
                            }
                            if (dirInfoID >= 0) {
                                for (SeafDirent dirent : seafDirents) {
                                    synchronized (imageInfosLock) {
                                        int imageID = dbHelper.addImage(dirInfoID, Utils.pathJoin(dataManager.getRepoDir(info.getRepoName(), info.getRepoId()), info.getDirPath(), dirent.name), dirent.size);
                                        if(imageID >= 0) {
                                            dbHelper.addWidgetImage(appWidgetId, dirInfoID, imageID);
                                        }
                                    }
                                }
                                usedDirIds.add(dirInfoID);
                            }
                        }
                    }else {
                        if(haveUpdated){
                            if(dataManager != null) {
                                List<SeafDirent> seafDirents = dataManager.getCachedDirents(info.getRepoId(), info.getDirPath());
                                synchronized (imageInfosLock) {
                                    dbHelper.setDirImagePreserve(dirInfoID, false);
                                }
                                for (SeafDirent dirent : seafDirents) {
                                    synchronized (imageInfosLock) {
                                        int imageID = dbHelper.addImage(dirInfoID, Utils.pathJoin(dataManager.getRepoDir(info.getRepoName(), info.getRepoId()), info.getDirPath(), dirent.name), dirent.size);
                                        if(imageID >= 0) {
                                            dbHelper.addWidgetImage(appWidgetId, dirInfoID, imageID);
                                        }
                                    }
                                }
                                synchronized (imageInfosLock) {
                                    dbHelper.deleteDirUnPreserveImage(dirInfoID);
                                }
                            }
                        }else{
                            synchronized (imageInfosLock) {
                                dbHelper.addWidgetDir(appWidgetId, dirInfoID);
                            }
                        }
                        usedDirIds.add(dirInfoID);

                    }
                    dirInfos.add(info);
                }

                synchronized (imageInfosLock) {
                    dbHelper.deleteUnusedWidgetDir(appWidgetId, usedDirIds);
                }

                if (haveUpdated) {
                    List<String> dirInfoStrs = new ArrayList<String>();
                    for (DirInfo dirInfo : dirInfos) {
                        dirInfoStrs.add(dirInfo.toString());
                    }
                    SettingsManager.instance().setLoopImagesWidgetDirInfo(appWidgetId, dirInfoStrs);
                }
            }

            synchronized (imageInfosLock) {
                dbHelper.deleteUnusedDirs();
            }
            Utils.utilsLogInfo(true, "=======Loopimages widget update end.");
        }
    }

    public void deleteAppWidgetInfo(int deleteAppWidgetSignal){
        if(deleteAppWidgetSignal == DELETE_NONE_WIDGETS){
            return;
        }
        synchronized (updateWidgetLock) {
            Context context = getApplicationContext();
            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
            int appWidgetIds[];
            if (deleteAppWidgetSignal != DELETE_ALL_WIDGETS) {
                appWidgetIds = new int[]{deleteAppWidgetSignal};
            } else {
                appWidgetIds = appWidgetManager.getAppWidgetIds(new ComponentName(context, LoopImagesWidget.class));
            }
            for(int appWidgetId: appWidgetIds) {
                synchronized (imageInfosLock) {
                    dbHelper.deleteWidget(appWidgetId);
                }
            }
            synchronized (imageInfosLock) {
                dbHelper.deleteUnusedDirs();
            }
        }
    }

    private DataManager getDataManager(String accountSignature){
        if(dataManagers.containsKey(accountSignature)){
            return dataManagers.get(accountSignature);
        }
        Account account = LoopImagesWidgetConfigureActivity.getAccount(getApplicationContext(), accountSignature);
        if(account == null){
            return null;
        }
        DataManager dataManager = new DataManager(account);
        dataManagers.put(accountSignature, dataManager);
        return dataManager;
    }

    private void clearAccountCache(){
        accounts.clear();
        dataManagers.clear();
    }

    private int addDirInfoToDB(DirInfo info){
        DataManager dataManager = getDataManager(info.getAccountSignature());
        if(dataManager == null){
            return -1;
        }
        List<SeafDirent> seafDirents = dataManager.getCachedDirents(info.getRepoId(), info.getDirPath());
        int dirInfoID = dbHelper.addDirInfo(info.getAccountSignature(), info.getRepoId(), info.getRepoName(), info.getDirId(), info.getDirPath());

        if(dirInfoID < 0){
            return -1;
        }
        for(SeafDirent dirent : seafDirents){
            int imageID = dbHelper.addImage(dirInfoID, Utils.pathJoin(dataManager.getRepoDir(info.getRepoName(), info.getRepoId()), info.getDirPath(), dirent.name), dirent.size);

        }
        return dirInfoID;
    }

    private boolean checkIsImage(String fileName){
        for (int j = 0; j < FILE_SUFIX.length; ++j) {
            if (fileName.toLowerCase().endsWith(FILE_SUFIX[j])) {
                return true;
            }
        }
        return false;
    }

    protected class TaskInfo{
        public int taskID;
        public int appWidgetId;
        public ImageInfo imageInfo;

        public TaskInfo(int taskID, int appWidgetId, ImageInfo imageInfo){
            this.taskID = taskID;
            this.appWidgetId = appWidgetId;
            this.imageInfo = imageInfo;
        }
    }

    protected final class UpdateHandler extends Handler {

        @RequiresApi(api = Build.VERSION_CODES.N)
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case UPDATE_MESSAGE:
                    ConcurrentAsyncTask.execute(new UpdateWdigetTask());
                    break;
                default:
                    break;
            }
        }
    }

    protected final class UpdateWdigetTask extends AsyncTask<Integer,Map<Integer, RemoteViews>, Map<Integer, RemoteViews>> {

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected Map<Integer, RemoteViews>  doInBackground(Integer... integers) {
            Map<Integer, RemoteViews> views = updateWidget();
            return views;
        }

        @Override
        protected void onProgressUpdate(Map<Integer, RemoteViews>... values) {
            super.onProgressUpdate(values);
        }

        @Override
        protected void onPostExecute(Map<Integer, RemoteViews> value) {
            super.onPostExecute(value);
            if(value == null) {
                return;
            }
            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(getApplicationContext());
            for (int appWidgetId : value.keySet()) {
                RemoteViews view = value.get(appWidgetId);
                if(view != null) {
                    appWidgetManager.updateAppWidget(appWidgetId, view);
                }
            }
        }
    }

    protected class UpdateImageInfoTask extends AsyncTask<Integer,Integer,Integer>{
        private int updateSignal;
        private int deleteSignal;

        public UpdateImageInfoTask(int updateSignal, int deleteSignal){
            this.updateSignal = updateSignal;
            this.deleteSignal = deleteSignal;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected Integer doInBackground(Integer... integers) {
            if(updateSignal != UPDATE_NONE_WIDGETS){
                updateImageInfo(updateSignal);
            }
            if(deleteSignal != DELETE_NONE_WIDGETS){
                deleteAppWidgetInfo(deleteSignal);
            }
            synchronized (updatingInfoLock){
                isUpdatingInfo = false;
            }
            return 0;
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            super.onProgressUpdate(values);
        }

        @Override
        protected void onPostExecute(Integer value) {
            super.onPostExecute(value);
        }
    }


}