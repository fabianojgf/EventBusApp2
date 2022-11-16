package com.example.eventbusapp.activity;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.example.eventbusapp.R;
import com.example.eventbusapp.event.exceptional.ExceptionEventA;
import com.example.eventbusapp.event.exceptional.ExceptionEventB;
import com.example.eventbusapp.service.FirstService;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.ExceptionalThreadMode;
import org.greenrobot.eventbus.Handle;

public class MainActivity extends AppCompatActivity {
    Button buttonIniciar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        buttonIniciar = findViewById(R.id.activityMainButtonIniciar);
        buttonIniciar.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                iniciar(v);
            }
        });

        Log.println(Log.VERBOSE, "EventBusAppTest", "MainActivity : onCreate : ---");
    }

    @Override
    public void onStart() {
        super.onStart();
        Log.println(Log.VERBOSE, "EventBusAppTest", "MainActivity : onStart : registerHandler");
        EventBus.getDefault(this).registerHandler(this);
    }

    @Override
    public void onStop() {
        super.onStop();
        //Log.println(Log.VERBOSE, "EventBusAppTest", "MainActivity : onStop : unregisterHandler");
        //EventBus.getDefault(this).unregister(this);

        buttonIniciar.setText("Proxima");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.println(Log.VERBOSE, "EventBusAppTest", "MainActivity : onDestroy : unregisterHandler");
        EventBus.getDefault(this).unregisterHandler(this);
    }

    @Handle(threadMode = ExceptionalThreadMode.MAIN)
    public void onExceptionEvent(ExceptionEventA exceptionEvent) {
        Log.println(Log.VERBOSE, "EventBusAppTest", "FirstActivity->MainActivity : onExceptionEvent( ExceptionEventA )");
        Toast.makeText(this, "[FirstActivity->MainActivity] : ExceptionEventA", Toast.LENGTH_SHORT).show();
    }

    @Handle(threadMode = ExceptionalThreadMode.MAIN)
    public void onExceptionEvent(ExceptionEventB exceptionEvent) {
        /* Do something */
        Log.println(Log.VERBOSE, "EventBusAppTest", "FirstActivity->MainActivity : onExceptionEvent( ExceptionEventB )");
        Toast.makeText(this, "[FirstActivity->MainActivity] : ExceptionEventB", Toast.LENGTH_LONG).show();
    }

    public void iniciar(View view) {
        Intent intent = new Intent(this, FirstActivity.class);
        startActivity(intent);

        //Intent intent2 = new Intent(this, FirstService.class);
        //startService(intent2);
    }
}