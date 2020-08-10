package com.example.eventbusapp.activity;

import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import com.example.eventbusapp.R;

import org.greenrobot.eventbus.EventBus;

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

    static {
        System.out.println("FirstActivity");
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
    }

    public void executeSum(View view) {
        try {
            Double numerador = Double.valueOf(editTextNumerador.getText().toString());
            Double denominador = Double.valueOf(editTextDenominador.getText().toString());
            Double result = numerador / denominador;
            editTextResultado.setText(String.valueOf(result));
        } catch(Exception e) {
            /*
            try {
                Intent intent = new Intent(this, SecondActivity.class);
                startActivity(intent);
            } finally {
                EventBus.getDefault().post(new ExceptionEvent(e));
            }
            */
            EventBus.getDefault(this).throwsException(new ExceptionEvent(e));
            System.out.println("PACK-APP:" + this.getApplication().getPackageName());
            System.out.println("PACK-APP-CXT:" + this.getApplicationContext().getPackageName());
        }
    }
}