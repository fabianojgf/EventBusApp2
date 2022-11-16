package com.example.eventbusapp.event.exceptional;

import org.greenrobot.eventbus.ExceptionEvent;

public class ExceptionEventA extends ExceptionEvent {
    public ExceptionEventA(Throwable throwable) {
        super(throwable);
    }
}
