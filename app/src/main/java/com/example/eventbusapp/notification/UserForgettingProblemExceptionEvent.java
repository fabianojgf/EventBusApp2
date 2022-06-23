package com.example.eventbusapp.notification;

import org.greenrobot.eventbus.parametric_scope.EvalObservedScopeData;
import org.greenrobot.eventbus.parametric_scope.ScopeExceptionEvent;

public class UserForgettingProblemExceptionEvent
        extends ScopeExceptionEvent<EvalObservedScopeData> {
    public UserForgettingProblemExceptionEvent(
            Throwable throwable, EvalObservedScopeData scopeData) {
        super(throwable, scopeData);
    }
}
