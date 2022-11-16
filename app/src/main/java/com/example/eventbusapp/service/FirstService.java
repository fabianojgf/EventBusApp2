package com.example.eventbusapp.service;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import com.example.eventbusapp.activity.FirstActivity;
import com.example.eventbusapp.event.exceptional.ExceptionEventB;

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
        Log.println(Log.VERBOSE, "EventBusAppTest", "FirstService: registerHandler");
        EventBus.getDefault(this).registerHandler(this);
        return value;
    }

    @Override
    public void onDestroy() {
        Log.println(Log.VERBOSE, "EventBusAppTest", "FirstService: unregisterHandler");
        EventBus.getDefault(this).unregisterHandler(this);
        super.onDestroy();
    }

    @Handle(threadMode = ExceptionalThreadMode.MAIN, actionMode = ExceptionalActionMode.LAZY_HANDLE)
    public void onExceptionEvent(ExceptionEventB exceptionEvent) {
        /* Do something */
        Log.println(Log.VERBOSE, "EventBusAppTest", "FirstActivity->FirstService : onExceptionEvent ( ExceptionEventB )");
        Toast.makeText(this, "[FirstActivity->FirstService] : ExceptionEventB", Toast.LENGTH_LONG).show();
    }
}
