package com.example.eventbusapp.activity;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.util.Log;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.example.eventbusapp.R;

import org.greenrobot.eventbus.ExceptionalThreadMode;
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

        Log.println(Log.VERBOSE, "EventBusTest", "SecondActivity: onCreate: register");
    }

    @Override
    public void onStart() {
        //EventBus.getDefault(this).register(this);
        //Log.println(Log.VERBOSE, "EventBusTest", "SecondActivity: onStart: register");
        super.onStart();
    }

    @Override
    public void onStop() {
        //EventBus.getDefault(this).unregister(this);
        //Log.println(Log.VERBOSE, "EventBusTest", "SecondActivity: onStop: unregister");
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        // EventBus.getDefault(this).unregister(this);
        Log.println(Log.VERBOSE, "EventBusTest", "SecondActivity: onDestroy: unregister");
        super.onDestroy();
    }

    //@Subscribe(threadMode = ThreadMode.MAIN)
    public void onExceptionEvent(FirstActivity.ExceptionEvent exceptionEvent) {
        /* Do something */
        Log.println(Log.VERBOSE, "EventBusTest", "FirstActivity->SecondActivity: onExceptionEvent");
        //textViewResponse.setText("FirstActivity->SecondActivity: onExceptionEvent");
        Toast.makeText(this, "[FA/SA] Ocorreu uma Exceção!", Toast.LENGTH_LONG).show();
    }
}