package com.example.eventbusapp.notification.scope;

import org.greenrobot.eventbus.parametric_scope.EvalExpectedScopeData;

import br.ufc.dc.eval.Expression;

public class UserAtHomeExpectedScope extends EvalExpectedScopeData {
    @Override
    protected Expression matchExpression() {
        return null;
    }
}
