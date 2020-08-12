package com.example.eventbusapp.activity;

import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
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
            EventBus.getDefault(this).throwsException(new ExceptionEvent2(e));
            //System.out.println("PACK-APP:" + this.getApplication().getPackageName());
            //System.out.println("PACK-APP-CXT:" + this.getApplicationContext().getPackageName());
/*
            Collection<URL> urls = ClasspathHelper.forPackage("com.example.eventbusapp.activity");

            ConfigurationBuilder configuration = new ConfigurationBuilder();
            configuration.setUrls(urls);

            final Class<?> myClazz = this.getClass();
            final String myPkg = myClazz.getPackage().getName();

            Collection<URL> urls = new ArrayList<URL>();
            try {
                urls.add(new URL("file://main/java/com/example/eventbusapp/activity"));
            } catch (MalformedURLException ex) {
                ex.printStackTrace();
            }
            */
            /*
            Collection<URL> urls2 = ClasspathHelper.forResource(
                    "com.example.eventbusapp.activity", this.getClassLoader());
            System.out.println("urls2: " + urls2);

            final ConfigurationBuilder config = new ConfigurationBuilder()
                    .setScanners(new ResourcesScanner(), new SubTypesScanner(), new TypeAnnotationsScanner())
                    .setUrls(urls2);
            System.out.println("urls: " + config.getUrls());

            Reflections reflections = new Reflections(config);
            */
/*
            Collection<URL> urls2 = new ArrayList<URL>();
            urls2.addAll(ClasspathHelper.forJavaClassPath());
            try {
                urls2.add(new URL("file:/com/example/eventbusapp/activity"));
                System.out.println("urls2: " + urls2);
            } catch (MalformedURLException ex) {
                ex.printStackTrace();
            }
*/
/*
            Collection<URL> urls2 = new ArrayList<URL>();
            try {
                urls2.add(new URL("file:/src/main/java/com/example/eventbusapp/activity"));
                System.out.println("urls2: " + urls2);
            } catch (MalformedURLException ex) {
                ex.printStackTrace();
            }
 */
            /*
            ConfigurationBuilder config = new ConfigurationBuilder()
                    .addClassLoaders(this.getClassLoader())
                    .addScanners(new SubTypesScanner())
                    .setUrls(urls2);
            System.out.println("urls: " + config.getUrls());

            Reflections reflections = new Reflections(config);
            */

//            try {
//                DexFile df = new DexFile(this.getPackageCodePath());
//                for (Enumeration<String> iter = df.entries(); iter.hasMoreElements();) {
//                    String s = iter.nextElement();
//                    if(s.contains(this.getPackageName())) {
//                        System.out.println("ClasseDex: " + s);
//
//                        try {
//                            Class<?> classe = Class.forName(s);
//                            if(classe.isAnnotationPresent(HandleClass.class)) {
//                                System.out.println("@HandleClass: " + classe.getName());
//
//                                Intent intent = new Intent(this, classe);
//                                startActivity(intent);
//                            }
//                        } catch (ClassNotFoundException ex) {
//                            ex.printStackTrace();
//                        }
//                    }
//                }
//            } catch (IOException e1) {
//                e1.printStackTrace();
//            }


//            final ClassLoader loader = Thread.currentThread().getContextClassLoader();
//
//            try {
//                Set<ClassPath.ClassInfo> classes = ClassPath.from(this.getClassLoader()).getAllClasses();
//                for(ClassPath.ClassInfo c: classes) {
//                    System.out.println("Classe: " + c.getName());
//                }
//            } catch (IOException ex) {
//                ex.printStackTrace();
//            }
//
//            URL u = ClassLoader.getSystemResource("com.example.eventbusapp");
//            System.out.println("Url: " + u);
//
//            File diretorio = new File("app/src/main/java/com/example/eventbusapp");
//            System.out.println("getAbsolutePath: " + diretorio.getAbsolutePath());
//            if(diretorio.exists()) {
//                System.out.println("getAbsolutePath: Diretorio");
//            }
//            Reflections reflections = new Reflections("com.example.eventbusapp.activity", new SubTypesScanner(false));
//
//            URL packageURL = this.getClassLoader().getResource("file:/src/main/java/com/example/eventbusapp");
//            System.out.println("URL: " + packageURL);
//
//            Set<Class<? extends Object>> subTypes = reflections.getSubTypesOf(Object.class);
//            System.out.println("subTypes: " + subTypes);
            /*
            Set<Class<?>> annotated = reflections.getTypesAnnotatedWith(HandleClass.class);
            System.out.println("annotated: " + annotated);
            */

//            for (Class<?> clazz : reflections.getSubTypesOf(Object.class)) {
//                System.out.println("@HandleClass: " + clazz.toString());
//            }
        }
    }
}