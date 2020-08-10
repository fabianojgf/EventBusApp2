package com.example.eventbusapp.activity;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.example.eventbusapp.R;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.ExceptionalThreadMode;
import org.greenrobot.eventbus.Handle;

public class MainActivity extends AppCompatActivity {
    Button buttonIniciar;

    static {
        System.out.println("MainActivity");
    }

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
    }

    @Override
    public void onStart() {
        EventBus.getDefault(this).registerHandler(this);
        Log.println(Log.VERBOSE, "EventBusTest", "MainActivity: registerHandler");
        super.onStart();
    }

    @Override
    public void onStop() {
        //EventBus.getDefault(this).unregister(this);
        //Log.println(Log.VERBOSE, "EventBusTest", "MainActivity: onStop: unregisterHandler");
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        EventBus.getDefault(this).unregisterHandler(this);
        Log.println(Log.VERBOSE, "EventBusTest", "MainActivity: onDestroy: unregisterHandler");
        super.onDestroy();
    }

    @Handle(threadMode = ExceptionalThreadMode.MAIN)
    public void onExceptionEvent(FirstActivity.ExceptionEvent exceptionEvent) {
        Log.println(Log.VERBOSE, "EventBusTest", "FirstActivity->MainActivity: onExceptionEvent");
        Toast.makeText(this, "[MA] Ocorreu uma Exceção!", Toast.LENGTH_SHORT).show();
    }

    public void iniciar(View view) {
        Intent intent = new Intent(this, FirstActivity.class);
        startActivity(intent);
    }
}