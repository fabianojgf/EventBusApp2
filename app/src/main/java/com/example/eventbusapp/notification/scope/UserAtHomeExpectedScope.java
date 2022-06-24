package com.example.eventbusapp.notification.scope;

import static br.ufc.dc.eval.Expression.*;

import android.location.Location;

import org.greenrobot.eventbus.parametric_scope.EvalExpectedScopeData;
import org.greenrobot.eventbus.parametric_scope.EvalObservedScopeData;

import br.ufc.dc.eval.EvaluationException;
import br.ufc.dc.eval.Expression;
import br.ufc.dc.eval.var.NumberConstant;
import br.ufc.dc.eval.var.NumberVariable;
import br.ufc.dc.eval.var.StringVariable;

public class UserAtHomeExpectedScope extends EvalExpectedScopeData {
    @Override
    protected Expression matchExpression(EvalObservedScopeData scopeData) {
        StringVariable userName = new StringVariable("userName");
        NumberVariable userLocationX = new NumberVariable("userLocationX");
        NumberVariable userLocationY = new NumberVariable("userLocationY");
        NumberVariable userHomeLocationX = new NumberVariable("userHomeLocationX");
        NumberVariable userHomeLocationY = new NumberVariable("userHomeLocationY");

        double radius = 10.0; //10 Meters.

        Expression exprUserLocation = null;

        try {
            Location userLocation = new Location("");
            userLocation.setLatitude(userLocationX.getValuation(
                    scopeData.getAssignment()).doubleValue());
            userLocation.setLongitude(userLocationY.getValuation(
                    scopeData.getAssignment()).doubleValue());

            Location userHomeLocation = new Location("");
            userLocation.setLatitude(userHomeLocationX.getValuation(
                    scopeData.getAssignment()).doubleValue());
            userLocation.setLongitude(userHomeLocationY.getValuation(
                    scopeData.getAssignment()).doubleValue());

            double distance = userLocation.distanceTo(userHomeLocation);

            exprUserLocation = Expression.lteq(
                    new NumberConstant(distance),
                    new NumberConstant(radius));
        } catch (EvaluationException e) {
            e.printStackTrace();
        }

        return exprUserLocation;
    }
}
