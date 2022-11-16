package com.example.eventbusapp.event.exceptional;

import org.greenrobot.eventbus.ExceptionEvent;

public class ExceptionEventGeneric extends ExceptionEvent {
    public ExceptionEventGeneric(Throwable throwable) {
        super(throwable);
    }
}
