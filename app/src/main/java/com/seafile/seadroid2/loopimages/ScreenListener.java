package com.seafile.seadroid2.loopimages;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.PowerManager;

public class ScreenListener {
    private Context context;
    private ScreenBroadcastReceiver screenBroadcastReceiver;
    private ScreenStateListener stateListener;

    public ScreenListener(Context context) {
        this.context = context;
        screenBroadcastReceiver = new ScreenBroadcastReceiver();
    }

    private class ScreenBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if(stateListener == null){
                return;
            }
            String action = intent.getAction();
            switch (action) {
                case Intent.ACTION_SCREEN_ON:
                    stateListener.onScreenOn();
                    break;
                case Intent.ACTION_SCREEN_OFF:
                    stateListener.onScreenOff();
                    break;
                case Intent.ACTION_USER_PRESENT:
                    stateListener.onUserPresent();
                    break;
            }
        }
    }


    private void registerListener() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        context.registerReceiver(screenBroadcastReceiver, filter);
    }

    public void unregisterListener() {
        context.unregisterReceiver(screenBroadcastReceiver);
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        unregisterListener();
    }

    private void getScreenState() {
        PowerManager manager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        if (manager.isInteractive()) {
            if (stateListener != null) {
                stateListener.onScreenOn();
            }
        } else {
            if (stateListener != null) {
                stateListener.onScreenOff();
            }
        }
    }

    public void begin(ScreenStateListener stateListener) {
        this.stateListener = stateListener;
        registerListener();
        getScreenState();
    }

    public interface ScreenStateListener {
        public void onScreenOn();

        public void onScreenOff();

        public void onUserPresent();
    }
}