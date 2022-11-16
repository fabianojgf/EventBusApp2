package com.example.eventbusapp.activity;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import com.example.eventbusapp.R;
import com.example.eventbusapp.event.exceptional.ExceptionEventA;
import com.example.eventbusapp.event.exceptional.ExceptionEventB;
import com.example.eventbusapp.event.exceptional.ExceptionEventC;
import com.example.eventbusapp.event.exceptional.ExceptionEventGeneric;

import org.greenrobot.eventbus.EventBus;

public class FirstActivity extends AppCompatActivity {
    Button buttonThrowExceptionA, buttonThrowExceptionB, buttonThrowExceptionC;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_first);

        buttonThrowExceptionA = findViewById(R.id.activityFirstButtonThrowExceptionA);
        buttonThrowExceptionB = findViewById(R.id.activityFirstButtonThrowExceptionB);
        buttonThrowExceptionC = findViewById(R.id.activityFirstButtonThrowExceptionC);

        buttonThrowExceptionA.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                throwExceptionA(v);
            }
        });
        buttonThrowExceptionB.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                throwExceptionB(v);
            }
        });
        buttonThrowExceptionC.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                throwExceptionC(v);
            }
        });

        Log.println(Log.VERBOSE, "EventBusAppTest", "FirstActivity: onCreate: ---");
    }

    public void throwExceptionA(View view) {
        try {
            throw new NullPointerException();
        } catch(NullPointerException e) {
            EventBus.getDefault(this).throwException(new ExceptionEventA(e));
        } catch(Exception e) {
            EventBus.getDefault(this).throwException(new ExceptionEventGeneric(e));
        }
    }

    public void throwExceptionB(View view) {
        try {
            throw new UnsupportedOperationException();
        } catch(UnsupportedOperationException e) {
            EventBus.getDefault(this).throwException(new ExceptionEventB(e));
        } catch(Exception e) {
            EventBus.getDefault(this).throwException(new ExceptionEventGeneric(e));
        }
    }

    public void throwExceptionC(View view) {
        try {
            throw new ClassNotFoundException();
        } catch(ClassNotFoundException e) {
            EventBus.getDefault(this).throwException(new ExceptionEventC(e));
        } catch(Exception e) {
            EventBus.getDefault(this).throwException(new ExceptionEventGeneric(e));
        }
    }
}