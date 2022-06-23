package org.greenrobot.eventbus.parametric_scope;

import org.greenrobot.eventbus.ExceptionEvent;

public abstract class ScopeExceptionEvent<T extends ObservedScopeData> extends ExceptionEvent {
    protected T scopeData;

    public ScopeExceptionEvent(Throwable throwable, T scopeData) {
        super(throwable);
        this.scopeData = scopeData;
    }

    public ObservedScopeData getScopeData() {
        return scopeData;
    }

    public void setScopeData(T scopeData) {
        this.scopeData = scopeData;
    }
}
