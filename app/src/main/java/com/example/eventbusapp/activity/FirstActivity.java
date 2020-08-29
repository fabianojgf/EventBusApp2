package com.example.eventbusapp.activity;

import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import com.example.eventbusapp.R;
import com.google.common.reflect.ClassPath;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.HandleClass;

import org.reflections.Reflections;
import org.reflections.scanners.ResourcesScanner;
import org.reflections.scanners.SubTypesScanner;
import org.reflections.scanners.TypeAnnotationsScanner;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;
import org.reflections.util.FilterBuilder;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Objects;
import java.util.Set;

import dalvik.system.DexFile;

public class FirstActivity extends AppCompatActivity {
    public static class ExceptionEvent {
        Exception exception;

        public ExceptionEvent(Exception exception) {
            this.exception = exception;
        }

        public Exception getException() {
            return exception;
        }

        public void setException(Exception exception) {
            this.exception = exception;
        }
    }

    public static class ExceptionEvent2 {
        Exception exception;

        public ExceptionEvent2(Exception exception) {
            this.exception = exception;
        }

        public Exception getException() {
            return exception;
        }

        public void setException(Exception exception) {
            this.exception = exception;
        }
    }

    Button buttonDivide;
    EditText editTextNumerador, editTextDenominador, editTextResultado;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_first);

        buttonDivide = findViewById(R.id.buttonDivide);
        buttonDivide.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                executeSum(v);
            }
        });

        editTextNumerador = findViewById(R.id.editTextNumerador);
        editTextDenominador = findViewById(R.id.editTextDenominador);
        editTextResultado = findViewById(R.id.editTextResultado);

        Log.println(Log.VERBOSE, "EventBusTest", "FirstActivity: onCreate: ---");
    }

    public void executeSum(View view) {
        try {
            Double numerador = Double.valueOf(editTextNumerador.getText().toString());
            Double denominador = Double.valueOf(editTextDenominador.getText().toString());
            Double result = numerador / denominador;
            editTextResultado.setText(String.valueOf(result));
        } catch(Exception e) {
            EventBus.getDefault(this).throwsException(new ExceptionEvent2(e));
        }
    }
}