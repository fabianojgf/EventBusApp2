package com.example.eventbusapp.service;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import com.example.eventbusapp.activity.FirstActivity;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.ExceptionalActionMode;
import org.greenrobot.eventbus.ExceptionalThreadMode;
import org.greenrobot.eventbus.Handle;

public class FirstService extends Service {
    public FirstService() {

    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        int value = super.onStartCommand(intent, flags, startId);
        Log.println(Log.VERBOSE, "EventBusTest", "FirstService: registerHandler");
        EventBus.getDefault(this).registerHandler(this);
        return value;
    }

    @Override
    public void onDestroy() {
        Log.println(Log.VERBOSE, "EventBusTest", "FirstService: unregisterHandler");
        EventBus.getDefault(this).unregisterHandler(this);
        super.onDestroy();
    }

    @Handle(threadMode = ExceptionalThreadMode.MAIN, actionMode = ExceptionalActionMode.LAZY_HANDLE)
    public void onExceptionEvent(FirstActivity.ExceptionEvent2 exceptionEvent) {
        /* Do something */
        Log.println(Log.VERBOSE, "EventBusTest", "FirstActivity->FirstService: onExceptionEvent");
        Toast.makeText(this, "[FA/FS] Ocorreu uma Exceção!", Toast.LENGTH_LONG).show();
    }
}
