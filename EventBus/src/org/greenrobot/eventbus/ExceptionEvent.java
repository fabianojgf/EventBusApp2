package org.greenrobot.eventbus;

public abstract class ExceptionEvent {
    protected Throwable throwable;

    public ExceptionEvent() {
        this(null);
    }

    public ExceptionEvent(Throwable throwable) {
        this.throwable = throwable;
    }

    public Throwable getThrowable() {
        return throwable;
    }

    public void setThrowable(Throwable throwable) {
        this.throwable = throwable;
    }
}