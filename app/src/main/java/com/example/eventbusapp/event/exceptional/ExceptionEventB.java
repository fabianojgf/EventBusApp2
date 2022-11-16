package com.example.eventbusapp.event.exceptional;

import org.greenrobot.eventbus.ExceptionEvent;

public class ExceptionEventB extends ExceptionEvent {
    public ExceptionEventB(Throwable throwable) {
        super(throwable);
    }
}
