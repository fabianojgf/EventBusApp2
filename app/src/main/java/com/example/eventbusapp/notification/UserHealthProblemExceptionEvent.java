package com.example.eventbusapp.notification;

import org.greenrobot.eventbus.parametric_scope.EvalObservedScopeData;
import org.greenrobot.eventbus.parametric_scope.ScopeExceptionEvent;

public class UserHealthProblemExceptionEvent
        extends ScopeExceptionEvent<EvalObservedScopeData> {
    public UserHealthProblemExceptionEvent(
            Throwable throwable, EvalObservedScopeData scopeData) {
        super(throwable, scopeData);
    }
}
