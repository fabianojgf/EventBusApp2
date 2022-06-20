package org.greenrobot.eventbus.parametric_scope;

import org.greenrobot.eventbus.ExceptionEvent;

public abstract class ScopeExceptionEvent extends ExceptionEvent {
    protected ObservedScopeData scopeData;

    public ScopeExceptionEvent(Throwable throwable, ObservedScopeData scopeData) {
        super(throwable);
        this.scopeData = scopeData;
    }

    public ObservedScopeData getScopeData() {
        return scopeData;
    }

    public void setScopeData(ObservedScopeData scopeData) {
        this.scopeData = scopeData;
    }
}
