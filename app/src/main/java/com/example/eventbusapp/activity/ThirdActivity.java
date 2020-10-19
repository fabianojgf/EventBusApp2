package com.example.eventbusapp.activity;

import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.eventbusapp.R;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.ExceptionalThreadMode;
import org.greenrobot.eventbus.Handle;

public class ThirdActivity extends AppCompatActivity {
    TextView textViewResponse3;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_third);

        textViewResponse3 = findViewById(R.id.textViewResponse3);

        Log.println(Log.VERBOSE, "EventBusTest", "ThirdActivity: onCreate: ---");
    }

    @Override
    public void onStart() {
        super.onStart();
        Log.println(Log.VERBOSE, "EventBusTest", "ThirdActivity: onStart: registerHandler");
        EventBus.getDefault(this).registerHandler(this);
    }

    @Override
    public void onStop() {
        super.onStop();
        Log.println(Log.VERBOSE, "EventBusTest", "ThirdActivity: onStop: unregisterHandler");
        EventBus.getDefault(this).unregisterHandler(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        //Log.println(Log.VERBOSE, "EventBusTest", "ThirdActivity: onDestroy: unregisterHandler");
        //EventBus.getDefault(this).unregisterHandler(this);
    }

    @Handle(threadMode = ExceptionalThreadMode.MAIN)
    public void onExceptionEvent(FirstActivity.ExceptionEvent2 exceptionEvent) {
        /* Do something */
        Log.println(Log.VERBOSE, "EventBusTest", "FirstActivity->ThirdActivity: onExceptionEvent");
        Toast.makeText(this, "[FA/TA] Ocorreu uma Exceção!", Toast.LENGTH_LONG).show();
    }
}