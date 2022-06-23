package com.example.eventbusapp.notification;

import org.greenrobot.eventbus.parametric_scope.EvalObservedScopeData;
import org.greenrobot.eventbus.parametric_scope.ScopeExceptionEvent;

public class UserIrritationProblemExceptionEvent
        extends ScopeExceptionEvent<EvalObservedScopeData> {
    public UserIrritationProblemExceptionEvent(
            Throwable throwable, EvalObservedScopeData scopeData) {
        super(throwable, scopeData);
    }
}
