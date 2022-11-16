package com.example.eventbusapp.activity;

import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.eventbusapp.R;
import com.example.eventbusapp.event.exceptional.ExceptionEventB;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.ExceptionalThreadMode;
import org.greenrobot.eventbus.Handle;

public class FourthActivity extends AppCompatActivity {
    TextView textViewResponse;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_third);

        textViewResponse = findViewById(R.id.activityFourthTextViewResponse);

        Log.println(Log.VERBOSE, "EventBusAppTest", "FourthActivity : onCreate: ---");
    }

    @Override
    public void onStart() {
        super.onStart();
        Log.println(Log.VERBOSE, "EventBusAppTest", "FourthActivity : onStart : registerHandler");
        EventBus.getDefault(this).registerHandler(this);
    }

    @Override
    public void onStop() {
        super.onStop();
        Log.println(Log.VERBOSE, "EventBusAppTest", "FourthActivity : onStop : unregisterHandler");
        EventBus.getDefault(this).unregisterHandler(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        //Log.println(Log.VERBOSE, "EventBusAppTest", "FourthActivity : onDestroy : unregisterHandler");
        //EventBus.getDefault(this).unregisterHandler(this);
    }

    @Handle(threadMode = ExceptionalThreadMode.MAIN)
    public void onExceptionEvent(ExceptionEventB exceptionEvent) {
        /* Do something */
        Log.println(Log.VERBOSE, "EventBusAppTest", "FirstActivity->FourthActivity : onExceptionEvent ( ExceptionEventB )");
        Toast.makeText(this, "[FirstActivity->FourthActivity] : ExceptionEventB", Toast.LENGTH_LONG).show();
    }
}