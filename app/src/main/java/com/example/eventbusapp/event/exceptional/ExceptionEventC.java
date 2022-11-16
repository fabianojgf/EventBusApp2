package com.example.eventbusapp.event.exceptional;

import org.greenrobot.eventbus.ExceptionEvent;

public class ExceptionEventC extends ExceptionEvent {
    public ExceptionEventC(Throwable throwable) {
        super(throwable);
    }
}
