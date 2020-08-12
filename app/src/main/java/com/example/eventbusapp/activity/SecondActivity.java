package com.example.eventbusapp.activity;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.util.Log;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.example.eventbusapp.R;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.ExceptionalThreadMode;
import org.greenrobot.eventbus.Handle;
import org.greenrobot.eventbus.HandleClass;

//import org.greenrobot.org.greenrobot.org.greenrobot.eventbus.EventBus;

@HandleClass(threadMode = ExceptionalThreadMode.MAIN)
public class SecondActivity extends AppCompatActivity {
    TextView textViewResponse;

    static {
        System.out.println("SecondActivity");
    }

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
        EventBus.getDefault(this).registerHandler(this);
        Log.println(Log.VERBOSE, "EventBusTest", "SecondActivity: onStart: registerHandler");
    }

    @Override
    public void onStop() {
        super.onStop();
        EventBus.getDefault(this).unregisterHandler(this);
        Log.println(Log.VERBOSE, "EventBusTest", "SecondActivity: onStop: unregisterHandler");
    }

    @Override
    protected void onDestroy() {
        //EventBus.getDefault(this).unregisterHandler(this);
        //Log.println(Log.VERBOSE, "EventBusTest", "SecondActivity: onDestroy: unregisterHandler");
        super.onDestroy();
    }

    @Handle(threadMode = ExceptionalThreadMode.MAIN)
    public void onExceptionEvent(FirstActivity.ExceptionEvent2 exceptionEvent) {
        /* Do something */
        Log.println(Log.VERBOSE, "EventBusTest", "FirstActivity->SecondActivity: onExceptionEvent");
        //textViewResponse.setText("FirstActivity->SecondActivity: onExceptionEvent");
        Toast.makeText(this, "[FA/SA] Ocorreu uma Exceção!", Toast.LENGTH_LONG).show();
    }
}