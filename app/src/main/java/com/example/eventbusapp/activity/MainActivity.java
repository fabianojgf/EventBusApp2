package com.example.eventbusapp.activity;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.example.eventbusapp.R;
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

        buttonIniciar = findViewById(R.id.buttonIniciar);
        buttonIniciar.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                iniciar(v);
            }
        });

        Log.println(Log.VERBOSE, "EventBusTest", "MainActivity: onCreate: ---");
    }

    @Override
    public void onStart() {
        super.onStart();
        Log.println(Log.VERBOSE, "EventBusTest", "MainActivity: registerHandler");
        EventBus.getDefault(this).registerHandler(this);
    }

    @Override
    public void onStop() {
        super.onStop();
        //Log.println(Log.VERBOSE, "EventBusTest", "MainActivity: onStop: unregisterHandler");
        //EventBus.getDefault(this).unregister(this);

        buttonIniciar.setText("Proxima");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.println(Log.VERBOSE, "EventBusTest", "MainActivity: onDestroy: unregisterHandler");
        EventBus.getDefault(this).unregisterHandler(this);
    }

    @Handle(threadMode = ExceptionalThreadMode.MAIN)
    public void onExceptionEvent(FirstActivity.ExceptionEvent exceptionEvent) {
        Log.println(Log.VERBOSE, "EventBusTest", "FirstActivity->MainActivity: onExceptionEvent");
        Toast.makeText(this, "[MA] Ocorreu uma Exceção!", Toast.LENGTH_SHORT).show();
    }

    @Handle(threadMode = ExceptionalThreadMode.MAIN)
    public void onExceptionEvent(FirstActivity.ExceptionEvent2 exceptionEvent) {
        /* Do something */
        Log.println(Log.VERBOSE, "EventBusTest", "FirstActivity->MainActivity: onExceptionEvent");
        Toast.makeText(this, "[FA/MA] Ocorreu uma Exceção!", Toast.LENGTH_LONG).show();
    }

    public void iniciar(View view) {
        Intent intent = new Intent(this, FirstActivity.class);
        startActivity(intent);

        //Intent intent2 = new Intent(this, FirstService.class);
        //startService(intent2);
    }
}