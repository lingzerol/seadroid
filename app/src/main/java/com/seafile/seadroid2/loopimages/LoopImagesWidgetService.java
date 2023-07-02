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
import com.seafile.seadroid2.SeafException;
import com.seafile.seadroid2.SettingsManager;
import com.seafile.seadroid2.account.Account;
import com.seafile.seadroid2.data.DataManager;
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

    private static final int WAIT_FOR_TIMEOUT = 10000;
    private static final int WAIT_TIME = 1000;

    private static final int ALARM_DURATION = 1 * 60 * 1000;
    private static final int UPDATE_DURATION = 5 * 1000;
    private static final int UPDATE_WIDGET_IMAGE_MESSAGE = 1000;
    private static final int CHECKT_DOWNLOAD_STATE = 1001;
    private static final int CHECKT_DOWNLOAD_STATE_DURATION = 5*1000;
    private static final int UPDATE_IMAGE_DELAY = 60 * 60 * 1000;
    private static final int ONCE_UPLOAD_IMAGE_NUM = 1;
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

    private Object updateWidgetInfoLock = new Object();
    private Object updateWidgetImageLock = new Object();
    private LinkedList<TaskInfo> tasksInProgress = Lists.newLinkedList();
    private LoopImagesDBHelper dbHelper = LoopImagesDBHelper.getInstance();
    private ScreenListener screenListener;

    private Map<String, DataManager> dataManagers = new HashMap<String, DataManager>();
    private Map<String, Account> accounts = new HashMap<String, Account>();

    private boolean isUpdatingWidgetInfo = false;
    private Object updatingWidgetInfoLock = new Object();

    private boolean canUpdateWidgetImage = true;
    private boolean isUpdatingWidgetImage = false;
    private Object updatingWidgetImageLock = new Object();

    private int updateWidgetInfoDelayTimes = 0;

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

    private void addUpdateWidgetInfoTask(int updateSignal, int deleteSignal){
        synchronized (updatingWidgetInfoLock){
            if(!isUpdatingWidgetInfo) {
                isUpdatingWidgetInfo = true;
                ConcurrentAsyncTask.execute(new UpdateWidgetInfoTask(updateSignal, deleteSignal));
            }
        }
    }

    private void sendUpdateWidgetImageMessage(){
        if(updateHandler == null){
            return;
        }
        synchronized (updatingWidgetImageLock){
            if(isUpdatingWidgetImage){
               return;
            }
        }
        Message message = updateHandler.obtainMessage();
        message.what = UPDATE_WIDGET_IMAGE_MESSAGE;
        updateHandler.sendMessageDelayed(message, UPDATE_DURATION);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(DEBUG_TAG, "onStartCommand");

        int updateAppWidgetSignal = UPDATE_NONE_WIDGETS;
        if(intent!=null) {
            updateAppWidgetSignal = intent.getIntExtra(UPDATE_IMAGE_INFO_SIGNAL, UPDATE_NONE_WIDGETS);
        }
        if(updateAppWidgetSignal != UPDATE_NONE_WIDGETS){
            addUpdateWidgetInfoTask(updateAppWidgetSignal, DELETE_NONE_WIDGETS);
            return START_STICKY;
        }

        int deleteAppWidgetSignal = DELETE_NONE_WIDGETS;
        if(intent != null){
            deleteAppWidgetSignal = intent.getIntExtra(DELETE_IMAGE_INFO_SIGNAL, DELETE_NONE_WIDGETS);
        }
        if(deleteAppWidgetSignal != DELETE_NONE_WIDGETS){
            addUpdateWidgetInfoTask(UPDATE_NONE_WIDGETS, deleteAppWidgetSignal);
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

        if(updateWidgetInfoDelayTimes < UPDATE_IMAGE_DELAY / ALARM_DURATION){
            ++updateWidgetInfoDelayTimes;
        }else{
            updateWidgetInfoDelayTimes = 0;
        }

        if(updateWidgetInfoDelayTimes == 1){
            addUpdateWidgetInfoTask(UPDATE_ALL_WIDGETS, DELETE_NONE_WIDGETS);
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

        screenListener = new ScreenListener(getApplicationContext());
        screenListener.begin(new ScreenListener.ScreenStateListener() {
            @Override
            public void onScreenOn() {
                synchronized (updateWidgetImageLock) {
                    canUpdateWidgetImage = true;
                }
                sendUpdateWidgetImageMessage();
            }

            @Override
            public void onScreenOff() {
                synchronized (updateWidgetImageLock) {
                    canUpdateWidgetImage = false;
                }
            }

            @Override
            public void onUserPresent() {
            }
        });

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

    private Map<Integer, RemoteViews> updateWidgetImage() {
        Map<Integer, RemoteViews> results = new HashMap<Integer, RemoteViews>();
        try {
            synchronized (updateWidgetImageLock) {
                if (!canUpdateWidgetImage) {
                    return results;
                }
                checkDownloadTasks();
//            Utils.utilsLogInfo(true, "=======Update Widget.");
                Context context = getApplicationContext();
                AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
                int appWidgetIds[] = appWidgetManager.getAppWidgetIds(new ComponentName(context, LoopImagesWidget.class));
                if (appWidgetIds.length <= 0) {
                    addUpdateWidgetInfoTask(UPDATE_ALL_WIDGETS, DELETE_NONE_WIDGETS);
                    return results;
                }
                int n = appWidgetIds.length;
                for (int appWidgetId : appWidgetIds) {
                    try {
                        Random rand = new Random();
//                Utils.utilsLogInfo(true, "=======Loopimages widget " + appWidgetId + " update start.");
                        boolean enableDownload = Utils.isWiFiOn() || LoopImagesWidgetConfigureActivity.getDataPlanAllowed(appWidgetId);
                        if (Utils.isNetworkOn() && enableDownload && tasksInProgress.size() < ONCE_UPLOAD_IMAGE_NUM * 2 * n) {
                            if (dbHelper.getImageNum(appWidgetId, true) < MAX_DOWNLOAD_IMAGE_NUM) {
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
                            if (item == null) {
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
                        if (Utils.isNetworkOn() && enableDownload && dbHelper.getImageNum(appWidgetId, true) >= MAX_DOWNLOAD_IMAGE_NUM) {
                            dbHelper.removeWidgetImages(appWidgetId, MAX_ACCESS_TIME);
                        }
//                Utils.utilsLogInfo(true, "=======Loopimages widget "+appWidgetId + " update end.");
                    }catch (Exception e){
                        Utils.utilsLogInfo(true, "LoopImageWidget update Widget image " + appWidgetId + " meets unknown error, " + e.toString());
                    }
                }
            }
        }catch (Exception e){
            Utils.utilsLogInfo(true, "LoopImageWidget update Widget image meets unknown error, " + e.toString());
        }
        return results;
    }

    private void updateWidgetInfo(int updateWidgetSignal){
        if(updateWidgetSignal == UPDATE_NONE_WIDGETS){
            return;
        }
        try {
            synchronized (updateWidgetInfoLock) {
                Utils.utilsLogInfo(true, "=======Loopimages widget update start.");
                Context context = getApplicationContext();
                AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);

                int[] appWidgetIds;
                if (updateWidgetSignal != UPDATE_ALL_WIDGETS) {
                    appWidgetIds = new int[]{updateWidgetSignal};
                } else {
                    appWidgetIds = appWidgetManager.getAppWidgetIds(new ComponentName(context, LoopImagesWidget.class));
                }
                Set<String> dirInfoSets = new HashSet<String>();
                for (int appWidgetId : appWidgetIds) {
                    try {
                        boolean haveUpdated = false;
                        List<Integer> usedDirIds = Lists.newArrayList();
                        List<DirInfo> dirInfos = Lists.newArrayList();
                        for (DirInfo info : LoopImagesWidgetConfigureActivity.getDirInfo(appWidgetId)) {
                            if (info == null) {
                                haveUpdated = true;
                                continue;
                            }
                            DataManager dataManager = getDataManager(info.getAccountSignature());
                            if (dataManager == null) {
                                haveUpdated = true;
                                continue;
                            }
                            int dirInfoID = -1;
                            synchronized (updateWidgetImageLock) {
                                dirInfoID = dbHelper.getDirInfoIDFromPath(info.getAccountSignature(), info.getRepoId(), info.getDirPath());
                            }
                            List<SeafDirent> seafDirents = getSeafDirentCache(dataManager, info.getRepoId(), info.getDirPath());
                            if(seafDirents == null){
                                dirInfos.add(info);
                                if(dirInfoID >= 0) {
                                    usedDirIds.add(dirInfoID);
                                }
                                continue;
                            }
                            String dirID = dataManager.getDirID(info.getRepoId(), info.getDirPath());
                            String prevInfoStr = info.toString();
                            boolean loadUnComplete = (seafDirents.size() > dbHelper.getDirImageNum(info.getAccountSignature(), info.getRepoId(), dirID));
                            boolean dirUpdate = (!dirID.equals(info.getDirId()));
                            if(dirUpdate){
                                if(!dirInfoSets.contains(prevInfoStr)) {
                                    if (dirInfoID >= 0) {
                                        synchronized (updateWidgetImageLock) {
                                            dbHelper.updateDirInfoDirID(dirInfoID, dirID);
                                        }
                                    }
                                }
                                info.setDirId(dirID);
                                haveUpdated = true;
                            }
                            synchronized (updateWidgetImageLock) {
                                dirInfoID = dbHelper.getDirInfoID(info.getAccountSignature(), info.getRepoId(), info.getDirId());
                            }
                            if(dirUpdate && !dirInfoSets.contains(prevInfoStr) || loadUnComplete || dirInfoID < 0){
                                if(dirInfoID < 0){
                                    synchronized (updateWidgetImageLock) {
                                        dirInfoID = dbHelper.addDirInfo(info.getAccountSignature(), info.getRepoId(), info.getRepoName(), info.getDirId(), info.getDirPath());
                                    }
                                }
                                if(dirInfoID >= 0) {
                                    synchronized (updateWidgetImageLock) {
                                        dbHelper.setDirImagePreserve(dirInfoID, false);
                                    }
                                    for (SeafDirent dirent : seafDirents) {
                                        int imageID = -1;
                                        synchronized (updateWidgetImageLock) {
                                            imageID = dbHelper.addImage(dirInfoID, Utils.pathJoin(dataManager.getRepoDir(info.getRepoName(), info.getRepoId()), info.getDirPath(), dirent.name), dirent.size);
                                        }
                                        if(imageID >= 0){
                                            synchronized (updateWidgetImageLock) {
                                                dbHelper.addWidgetImage(appWidgetId, dirInfoID, imageID);
                                            }
                                        }
                                    }
                                    synchronized (updateWidgetImageLock) {
                                        dbHelper.deleteDirUnPreserveImage(dirInfoID);
                                    }
                                }
                                if(dirUpdate){
                                    dirInfoSets.add(prevInfoStr);
                                }
                            }
                            if (dirInfoID >= 0) {
                                if(!dbHelper.widgetContainDir(appWidgetId, dirInfoID)){
                                    synchronized (updateWidgetImageLock) {
                                        dbHelper.addWidgetDir(appWidgetId, dirInfoID);
                                    }
                                }
                                usedDirIds.add(dirInfoID);
                            }
                            dirInfos.add(info);
                        }

                        synchronized (updateWidgetImageLock) {
                            dbHelper.deleteUnusedWidgetDir(appWidgetId, usedDirIds);
                        }

                        if (haveUpdated) {
                            List<String> dirInfoStrs = new ArrayList<String>();
                            for (DirInfo dirInfo : dirInfos) {
                                dirInfoStrs.add(dirInfo.toString());
                            }
                            SettingsManager.instance().setLoopImagesWidgetDirInfo(appWidgetId, dirInfoStrs);
                        }
                    }catch (Exception e){
                        Utils.utilsLogInfo(true, "LoopImageWidget update Widget info " + appWidgetId + " meets unknown error, " + e.toString());
                    }
                }
                synchronized (updateWidgetImageLock) {
                    dbHelper.deleteUnusedDirs();
                }
                Utils.utilsLogInfo(true, "=======Loopimages widget update end.");
            }
        }catch (Exception e){
            Utils.utilsLogInfo(true, "LoopImageWidget update Widget info meets unknown error, " + e.toString());
        }
    }

    public void deleteAppWidgetInfo(int deleteAppWidgetSignal){
        if(deleteAppWidgetSignal == DELETE_NONE_WIDGETS){
            return;
        }
        synchronized (updateWidgetInfoLock) {
            Context context = getApplicationContext();
            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
            int appWidgetIds[];
            if (deleteAppWidgetSignal != DELETE_ALL_WIDGETS) {
                appWidgetIds = new int[]{deleteAppWidgetSignal};
            } else {
                appWidgetIds = appWidgetManager.getAppWidgetIds(new ComponentName(context, LoopImagesWidget.class));
            }
            for(int appWidgetId: appWidgetIds) {
                synchronized (updateWidgetImageLock) {
                    dbHelper.deleteWidget(appWidgetId);
                }
            }
            synchronized (updateWidgetImageLock) {
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

    private List<SeafDirent> getSeafDirentCache(DataManager dataManager, String repoID, String dirPath){
        if(dataManager == null){
            return null;
        }
        List<SeafDirent> seafDirents = dataManager.getCachedDirents(repoID, dirPath);
        for (int i=0; i< WAIT_FOR_TIMEOUT/WAIT_TIME && seafDirents == null; ++i) {
            // Log.d(DEBUG_TAG, "waiting for transfer service");
            try {
                Thread.sleep(WAIT_TIME);
                seafDirents = dataManager.getDirentsFromServer(repoID, dirPath);
            }catch (InterruptedException e){
                Utils.utilsLogInfo(true, "The thread is interrupted, "+e.toString());
            }catch (SeafException e){
                Utils.utilsLogInfo(true, "SeafConnection error, " + e.toString());
            }
        }
        return seafDirents;
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

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case UPDATE_WIDGET_IMAGE_MESSAGE:
                    ConcurrentAsyncTask.execute(new UpdateWidgetImageTask());
                    break;
                default:
                    break;
            }
        }
    }

    protected final class UpdateWidgetImageTask extends AsyncTask<Integer,Map<Integer, RemoteViews>, Map<Integer, RemoteViews>> {

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected Map<Integer, RemoteViews>  doInBackground(Integer... integers) {
            synchronized (updateWidgetImageLock){
                isUpdatingWidgetImage = false;
            }
            Map<Integer, RemoteViews> views = updateWidgetImage();
            return views;
        }

        @Override
        protected void onProgressUpdate(Map<Integer, RemoteViews>... values) {
            super.onProgressUpdate(values);
        }

        @Override
        protected void onPostExecute(Map<Integer, RemoteViews> value) {
            super.onPostExecute(value);
            boolean canUpdate = false;
            synchronized (updateWidgetImageLock){
                canUpdate = canUpdateWidgetImage;
            }
            if(canUpdate){
                sendUpdateWidgetImageMessage();
            }
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

    protected class UpdateWidgetInfoTask extends AsyncTask<Integer,Integer,Integer>{
        private int updateSignal;
        private int deleteSignal;

        public UpdateWidgetInfoTask(int updateSignal, int deleteSignal){
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
                updateWidgetInfo(updateSignal);
            }
            if(deleteSignal != DELETE_NONE_WIDGETS){
                deleteAppWidgetInfo(deleteSignal);
            }
            synchronized (updatingWidgetInfoLock){
                isUpdatingWidgetInfo = false;
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