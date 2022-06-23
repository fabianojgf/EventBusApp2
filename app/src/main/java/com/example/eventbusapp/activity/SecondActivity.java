package com.example.eventbusapp.activity;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import com.example.eventbusapp.R;
import com.example.eventbusapp.notification.UserHealthProblemExceptionEvent;
import com.example.eventbusapp.notification.scope.UserAtHomeExpectedScope;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.ExceptionalActionMode;
import org.greenrobot.eventbus.ExceptionalThreadMode;
import org.greenrobot.eventbus.Handle;

public class SecondActivity extends AppCompatActivity {
    TextView textViewResponse;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_second);

        textViewResponse = findViewById(R.id.textViewResponse);

        Log.println(Log.VERBOSE, "EventBusTest", "SecondActivity: onCreate: ---");
    }

    @Override
    public void onStart() {
        super.onStart();
        Log.println(Log.VERBOSE, "EventBusTest", "SecondActivity: onStart: registerHandler");
        EventBus.getDefault(this).registerHandler(this);
    }

    @Override
    public void onStop() {
        super.onStop();
        Log.println(Log.VERBOSE, "EventBusTest", "SecondActivity: onStop: unregisterHandler");
        EventBus.getDefault(this).unregisterHandler(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        //Log.println(Log.VERBOSE, "EventBusTest", "SecondActivity: onDestroy: unregisterHandler");
        //EventBus.getDefault(this).unregisterHandler(this);
    }

    @Handle(threadMode = ExceptionalThreadMode.MAIN,
            actionMode = ExceptionalActionMode.LAZY_HANDLE,
            expectedScopeClass = UserAtHomeExpectedScope.class)
    public void onExceptionEvent(UserHealthProblemExceptionEvent exceptionEvent) {
        /* Do something */
        Log.println(Log.VERBOSE, "EventBusTest", "FirstActivity->SecondActivity: onExceptionEvent");
        Toast.makeText(this, "UserHealthProblemExceptionEvent ---> USER AT WORK!!!", Toast.LENGTH_LONG).show();
    }
}