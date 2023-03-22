package com.seafile.seadroid2.upload;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

/**
 * Camera Sync Service.
 *
 * This service is started and stopped by the Android System.
 */
public class UploadSyncService extends Service {

    private static UploadSyncAdapter sSyncAdapter = null;
    private static final Object sSyncAdapterLock = new Object();

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onCreate() {
        synchronized (sSyncAdapterLock) {
            if (sSyncAdapter == null) {
                sSyncAdapter = new UploadSyncAdapter(getApplicationContext());
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return sSyncAdapter.getSyncAdapterBinder();
    }

}
